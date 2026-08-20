#include <aaudio/AAudio.h>
#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <memory>
#include <string>
#include <thread>
#include <vector>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/channel_layout.h>
#include <libswresample/swresample.h>
}

namespace {
    constexpr int kOutputChannels = 2;
    constexpr int kEqBands = 10;
    constexpr size_t kRingFrames = 96'000;
    constexpr int kNoSlot = -1;
    constexpr int kTransitionNone = 0;
    constexpr int kTransitionGaplessPromoted = 1;
    constexpr int kTransitionCrossfadeStarted = 2;

    struct GlobalDspConfig {
        float preamp_gain_db = 0.0f;
        float stereo_width = 1.0f;
        float eq_band_gains_db[kEqBands]{};
    };

    struct Biquad {
        bool active = false;
        float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f, a1 = 0.0f, a2 = 0.0f;
        float left_z1 = 0.0f, left_z2 = 0.0f, right_z1 = 0.0f, right_z2 = 0.0f;
    };

    struct SourceSlot {
        AVFormatContext *format = nullptr;
        AVCodecContext *codec = nullptr;
        AVPacket *packet = nullptr;
        AVFrame *frame = nullptr;
        SwrContext *resampler = nullptr;
        int audio_stream = -1;
        int output_rate = 48'000;
        std::vector<float> ring = std::vector<float>(kRingFrames * kOutputChannels);
        std::atomic<size_t> read_frame{0};
        std::atomic<size_t> write_frame{0};
        std::atomic<bool> stopping{false};
        std::atomic<bool> opened{false};
        std::atomic<bool> input_exhausted{false};
        std::atomic<bool> finished{false};
        std::atomic<bool> seek_pending{false};
        std::atomic<int64_t> seek_us{0};
        std::atomic<int64_t> position_base_us{0};
        std::atomic<int64_t> rendered_frames{0};
        std::atomic<float> normalization_gain_db{0.0f};
        std::thread decode_thread;
    };

    struct PlaybackEngine {
        SourceSlot slots[2];
        AAudioStream *stream = nullptr;
        std::atomic<bool> playing{false};
        std::atomic<bool> output_disconnected{false};
        std::atomic<int> active_slot{kNoSlot};
        std::atomic<int> preloaded_slot{kNoSlot};
        std::atomic<int> outgoing_slot{kNoSlot};
        std::atomic<bool> crossfading{false};
        std::atomic<int64_t> fade_frames_total{0};
        std::atomic<int64_t> fade_frames_rendered{0};
        std::atomic<int> transition_event{kTransitionNone};
        std::atomic<int> callback_depth{0};
        // Config instances live for the lifetime of the engine. The callback only
        // atomically reads a pointer; no allocation, ref-counting or locking occurs.
        std::vector<std::unique_ptr<GlobalDspConfig>> dsp_configs;
        std::atomic<const GlobalDspConfig *> dsp_config{nullptr};
        const GlobalDspConfig *applied_dsp_config = nullptr;
        std::array<Biquad, kEqBands> eq_filters{};
        int output_rate = 48'000;
    };

    void throw_java(JNIEnv *env, const char *message) {
        jclass exception = env->FindClass("java/lang/IllegalStateException");
        if (exception != nullptr) env->ThrowNew(exception, message);
    }

    size_t available(const SourceSlot &slot) {
        const size_t read = slot.read_frame.load(std::memory_order_acquire);
        const size_t write = slot.write_frame.load(std::memory_order_acquire);
        return write >= read ? write - read : kRingFrames - read + write;
    }

    void free_media(SourceSlot &slot) {
        swr_free(&slot.resampler);
        av_frame_free(&slot.frame);
        av_packet_free(&slot.packet);
        avcodec_free_context(&slot.codec);
        avformat_close_input(&slot.format);
        slot.audio_stream = -1;
    }

    void release_slot(SourceSlot &slot) {
        slot.stopping.store(true, std::memory_order_release);
        if (slot.decode_thread.joinable()) slot.decode_thread.join();
        free_media(slot);
        slot.read_frame.store(0, std::memory_order_release);
        slot.write_frame.store(0, std::memory_order_release);
        slot.opened.store(false, std::memory_order_release);
        slot.input_exhausted.store(false, std::memory_order_release);
        slot.finished.store(false, std::memory_order_release);
    }

    void wait_for_callback_quiescence(PlaybackEngine &engine) {
        while (engine.callback_depth.load(std::memory_order_acquire) != 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
        }
    }

    bool push_samples(SourceSlot &slot, const float *samples, size_t frames) {
        for (size_t i = 0; i < frames; ++i) {
            while (!slot.stopping.load(std::memory_order_acquire) && available(slot) >= kRingFrames - 1) {
                // The audio callback never signals this wait: that would be a callback
                // side effect. A bounded timed wait is sufficient for a decode worker.
                std::this_thread::sleep_for(std::chrono::milliseconds(2));
            }
            if (slot.stopping.load(std::memory_order_acquire)) return false;
            const size_t write = slot.write_frame.load(std::memory_order_relaxed);
            slot.ring[write * kOutputChannels] = samples[i * kOutputChannels];
            slot.ring[write * kOutputChannels + 1] = samples[i * kOutputChannels + 1];
            slot.write_frame.store((write + 1) % kRingFrames, std::memory_order_release);
        }
        return true;
    }

    void decoder_loop(SourceSlot &slot) {
        std::vector<float> output;
        int64_t discard_before_us = -1;
        while (!slot.stopping.load(std::memory_order_acquire)) {
            if (slot.seek_pending.exchange(false, std::memory_order_acq_rel)) {
                const int64_t target_us = slot.seek_us.load(std::memory_order_acquire);
                const int64_t target = av_rescale_q(target_us, AVRational{1, AV_TIME_BASE}, slot.format->streams[slot.audio_stream]->time_base);
                av_seek_frame(slot.format, slot.audio_stream, target, AVSEEK_FLAG_BACKWARD);
                avcodec_flush_buffers(slot.codec);
                swr_close(slot.resampler);
                swr_init(slot.resampler);
                slot.read_frame.store(0, std::memory_order_release);
                slot.write_frame.store(0, std::memory_order_release);
                slot.input_exhausted.store(false, std::memory_order_release);
                slot.finished.store(false, std::memory_order_release);
                // av_seek_frame seeks to a preceding keyframe. Do not expose
                // decoded PCM before the requested timestamp to the callback.
                discard_before_us = target_us;
            }
            const int read = av_read_frame(slot.format, slot.packet);
            if (read < 0) {
                slot.input_exhausted.store(true, std::memory_order_release);
                return;
            }
            if (slot.packet->stream_index == slot.audio_stream && avcodec_send_packet(slot.codec, slot.packet) >= 0) {
                while (avcodec_receive_frame(slot.codec, slot.frame) >= 0) {
                    const int out_frames = swr_get_out_samples(slot.resampler, slot.frame->nb_samples);
                    output.resize(static_cast<size_t>(out_frames) * kOutputChannels);
                    uint8_t *out_data[] = {reinterpret_cast<uint8_t *>(output.data())};
                    const int converted = swr_convert(slot.resampler, out_data, out_frames,
                            const_cast<const uint8_t **>(slot.frame->extended_data), slot.frame->nb_samples);
                    if (converted > 0) {
                        int skip_frames = 0;
                        const int64_t frame_pts = slot.frame->best_effort_timestamp;
                        if (discard_before_us >= 0 && frame_pts != AV_NOPTS_VALUE) {
                            const int64_t frame_start_us = av_rescale_q(
                                    frame_pts,
                                    slot.format->streams[slot.audio_stream]->time_base,
                                    AVRational{1, AV_TIME_BASE});
                            const int64_t frame_duration_us = av_rescale(
                                    slot.frame->nb_samples, AV_TIME_BASE, slot.codec->sample_rate);
                            if (frame_start_us + frame_duration_us <= discard_before_us) {
                                continue;
                            }
                            if (frame_start_us < discard_before_us) {
                                skip_frames = static_cast<int>(std::min<int64_t>(
                                        converted,
                                        av_rescale(discard_before_us - frame_start_us, slot.output_rate, AV_TIME_BASE)));
                            }
                            discard_before_us = -1;
                        }
                        if (skip_frames < converted && !push_samples(
                                slot,
                                output.data() + static_cast<size_t>(skip_frames) * kOutputChannels,
                                converted - skip_frames))
                            return;
                    }
                }
            }
            av_packet_unref(slot.packet);
        }
    }

    bool open_slot(SourceSlot &slot, int fd, int output_rate, float normalization_gain_db, std::string &error) {
        release_slot(slot);
        const std::string path = "/proc/self/fd/" + std::to_string(fd);
        if (avformat_open_input(&slot.format, path.c_str(), nullptr, nullptr) < 0 ||
                avformat_find_stream_info(slot.format, nullptr) < 0) {
            error = "FFmpeg could not open this audio file";
            free_media(slot);
            return false;
        }
        slot.audio_stream = av_find_best_stream(slot.format, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
        if (slot.audio_stream < 0) {
            error = "No audio stream in file";
            free_media(slot);
            return false;
        }
        const AVCodecParameters *parameters = slot.format->streams[slot.audio_stream]->codecpar;
        const AVCodec *decoder = avcodec_find_decoder(parameters->codec_id);
        if (decoder == nullptr) {
            error = "This FFmpeg build has no decoder for the audio stream";
            free_media(slot);
            return false;
        }
        slot.codec = avcodec_alloc_context3(decoder);
        if (slot.codec == nullptr || avcodec_parameters_to_context(slot.codec, parameters) < 0 || avcodec_open2(slot.codec, decoder, nullptr) < 0) {
            error = "FFmpeg could not initialize the decoder";
            free_media(slot);
            return false;
        }
        AVChannelLayout stereo = AV_CHANNEL_LAYOUT_STEREO;
        if (swr_alloc_set_opts2(&slot.resampler, &stereo, AV_SAMPLE_FMT_FLT, output_rate,
                &slot.codec->ch_layout, slot.codec->sample_fmt, slot.codec->sample_rate, 0, nullptr) < 0 ||
                swr_init(slot.resampler) < 0) {
            error = "FFmpeg could not configure PCM output";
            free_media(slot);
            return false;
        }
        slot.packet = av_packet_alloc();
        slot.frame = av_frame_alloc();
        if (slot.packet == nullptr || slot.frame == nullptr) {
            error = "FFmpeg could not allocate decode buffers";
            free_media(slot);
            return false;
        }
        slot.output_rate = output_rate;
        slot.stopping.store(false, std::memory_order_release);
        slot.normalization_gain_db.store(normalization_gain_db, std::memory_order_release);
        slot.position_base_us.store(0, std::memory_order_release);
        slot.rendered_frames.store(0, std::memory_order_release);
        slot.opened.store(true, std::memory_order_release);
        slot.decode_thread = std::thread(decoder_loop, std::ref(slot));
        return true;
    }

    bool read_frame(SourceSlot &slot, float &left, float &right) {
        const size_t read = slot.read_frame.load(std::memory_order_relaxed);
        if (read == slot.write_frame.load(std::memory_order_acquire)) {
            if (slot.input_exhausted.load(std::memory_order_acquire)) slot.finished.store(true, std::memory_order_release);
            left = right = 0.0f;
            return false;
        }
        const float gain = std::pow(10.0f, slot.normalization_gain_db.load(std::memory_order_relaxed) / 20.0f);
        left = slot.ring[read * kOutputChannels] * gain;
        right = slot.ring[read * kOutputChannels + 1] * gain;
        slot.read_frame.store((read + 1) % kRingFrames, std::memory_order_release);
        slot.rendered_frames.fetch_add(1, std::memory_order_relaxed);
        return true;
    }

    void promote_gapless(PlaybackEngine &engine) {
        const int incoming = engine.preloaded_slot.exchange(kNoSlot, std::memory_order_acq_rel);
        if (incoming == kNoSlot) return;
        const int outgoing = engine.active_slot.exchange(incoming, std::memory_order_acq_rel);
        // Unlike a crossfade, the old source has already exhausted its ring.
        // It can be retired now and reclaimed by the subsequent preload.
        if (outgoing != kNoSlot) engine.slots[outgoing].stopping.store(true, std::memory_order_release);
        engine.transition_event.store(kTransitionGaplessPromoted, std::memory_order_release);
    }

    void finish_fade_in_callback(PlaybackEngine &engine) {
        const int outgoing = engine.outgoing_slot.exchange(kNoSlot, std::memory_order_acq_rel);
        if (outgoing != kNoSlot) engine.slots[outgoing].stopping.store(true, std::memory_order_release);
        engine.crossfading.store(false, std::memory_order_release);
    }

    constexpr float kEqFrequenciesHz[kEqBands] = {32.0f, 64.0f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f};

    void configure_eq(PlaybackEngine &engine, const GlobalDspConfig *config) {
        engine.applied_dsp_config = config;
        constexpr float q = 1.0f;
        for (int i = 0; i < kEqBands; ++i) {
            auto &filter = engine.eq_filters[i];
            filter = Biquad{};
            const float gain = config == nullptr ? 0.0f : config->eq_band_gains_db[i];
            if (gain == 0.0f) continue;
            filter.active = true;
            const float omega = 2.0f * static_cast<float>(M_PI) * kEqFrequenciesHz[i] / static_cast<float>(engine.output_rate);
            const float alpha = std::sin(omega) / (2.0f * q);
            const float a = std::pow(10.0f, gain / 40.0f);
            const float a0 = 1.0f + alpha / a;
            filter.b0 = (1.0f + alpha * a) / a0;
            filter.b1 = (-2.0f * std::cos(omega)) / a0;
            filter.b2 = (1.0f - alpha * a) / a0;
            filter.a1 = (-2.0f * std::cos(omega)) / a0;
            filter.a2 = (1.0f - alpha / a) / a0;
        }
    }

    float filter_sample(float input, float &z1, float &z2, const Biquad &filter) {
        const float output = filter.b0 * input + z1;
        z1 = filter.b1 * input - filter.a1 * output + z2;
        z2 = filter.b2 * input - filter.a2 * output;
        return output;
    }

    aaudio_data_callback_result_t audio_callback(AAudioStream *, void *user, void *audio_data, int32_t frames) {
        auto &engine = *static_cast<PlaybackEngine *>(user);
        engine.callback_depth.fetch_add(1, std::memory_order_acq_rel);
        auto *output = static_cast<float *>(audio_data);
        const GlobalDspConfig *dsp = engine.dsp_config.load(std::memory_order_acquire);
        if (dsp != engine.applied_dsp_config) configure_eq(engine, dsp);
        for (int32_t i = 0; i < frames; ++i) {
            float left = 0.0f;
            float right = 0.0f;
            if (engine.playing.load(std::memory_order_relaxed)) {
                const int active = engine.active_slot.load(std::memory_order_acquire);
                if (active != kNoSlot) {
                    if (engine.crossfading.load(std::memory_order_acquire)) {
                        const int outgoing = engine.outgoing_slot.load(std::memory_order_acquire);
                        float in_left, in_right, out_left, out_right;
                        read_frame(engine.slots[active], in_left, in_right);
                        if (outgoing != kNoSlot) read_frame(engine.slots[outgoing], out_left, out_right); else out_left = out_right = 0.0f;
                        const int64_t total = std::max<int64_t>(1, engine.fade_frames_total.load(std::memory_order_relaxed));
                        const int64_t at = std::min(engine.fade_frames_rendered.fetch_add(1, std::memory_order_relaxed), total);
                        const float phase = static_cast<float>(at) / static_cast<float>(total) * static_cast<float>(M_PI_2);
                        left = out_left * std::cos(phase) + in_left * std::sin(phase);
                        right = out_right * std::cos(phase) + in_right * std::sin(phase);
                        if (at + 1 >= total) finish_fade_in_callback(engine);
                    } else if (!read_frame(engine.slots[active], left, right)) {
                        if (engine.preloaded_slot.load(std::memory_order_acquire) != kNoSlot) {
                            promote_gapless(engine);
                            const int promoted = engine.active_slot.load(std::memory_order_acquire);
                            if (promoted != kNoSlot) read_frame(engine.slots[promoted], left, right);
                        } else if (engine.slots[active].finished.load(std::memory_order_acquire)) {
                            engine.playing.store(false, std::memory_order_release);
                        }
                    }
                }
            }
            for (auto &filter : engine.eq_filters) {
                if (!filter.active) continue;
                left = filter_sample(left, filter.left_z1, filter.left_z2, filter);
                right = filter_sample(right, filter.right_z1, filter.right_z2, filter);
            }
            const float preamp = dsp == nullptr ? 1.0f : std::pow(10.0f, dsp->preamp_gain_db / 20.0f);
            const float width = dsp == nullptr ? 1.0f : dsp->stereo_width;
            const float mid = (left + right) * 0.5f;
            const float side = (left - right) * 0.5f * width;
            output[i * kOutputChannels] = (mid + side) * preamp;
            output[i * kOutputChannels + 1] = (mid - side) * preamp;
        }
        engine.callback_depth.fetch_sub(1, std::memory_order_release);
        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    void error_callback(AAudioStream *, void *user, aaudio_result_t error) {
        auto &engine = *static_cast<PlaybackEngine *>(user);
        if (error == AAUDIO_ERROR_DISCONNECTED) {
            engine.output_disconnected.store(true, std::memory_order_release);
            engine.playing.store(false, std::memory_order_release);
        }
    }

    bool open_output(PlaybackEngine &engine, std::string &error) {
        if (engine.stream != nullptr) return true;
        AAudioStreamBuilder *builder = nullptr;
        AAudio_createStreamBuilder(&builder);
        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setChannelCount(builder, kOutputChannels);
        AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_POWER_SAVING);
        AAudioStreamBuilder_setDataCallback(builder, audio_callback, &engine);
        AAudioStreamBuilder_setErrorCallback(builder, error_callback, &engine);
        const aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &engine.stream);
        AAudioStreamBuilder_delete(builder);
        if (result != AAUDIO_OK) {
            error = "AAudio output is unavailable";
            return false;
        }
        engine.output_rate = AAudioStream_getSampleRate(engine.stream);
        return true;
    }

    void close_output(PlaybackEngine &engine) {
        if (engine.stream != nullptr) {
            AAudioStream_requestStop(engine.stream);
            AAudioStream_close(engine.stream);
            engine.stream = nullptr;
        }
    }

    void snap_fade(PlaybackEngine &engine) {
        if (!engine.crossfading.exchange(false, std::memory_order_acq_rel)) return;
        const int outgoing = engine.outgoing_slot.exchange(kNoSlot, std::memory_order_acq_rel);
        if (outgoing != kNoSlot) engine.slots[outgoing].stopping.store(true, std::memory_order_release);
    }

    void destroy_engine(PlaybackEngine &engine) {
        engine.playing.store(false, std::memory_order_release);
        for (auto &slot: engine.slots) slot.stopping.store(true, std::memory_order_release);
        close_output(engine);
        for (auto &slot: engine.slots) release_slot(slot);
    }

    int idle_slot_for_preload(PlaybackEngine &engine) {
        const int active = engine.active_slot.load(std::memory_order_acquire);
        const int preloaded = engine.preloaded_slot.load(std::memory_order_acquire);
        const int outgoing = engine.outgoing_slot.load(std::memory_order_acquire);
        // The outgoing slot remains readable for the entire fade. It must not
        // be selected for another preload until the callback has retired it.
        for (int i = 0; i < 2; ++i) if (i != active && i != preloaded && i != outgoing) return i;
        return kNoSlot;
    }
}  // namespace

extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeCreate(JNIEnv *, jclass) {
    auto *engine = new PlaybackEngine();
    auto config = std::make_unique<GlobalDspConfig>();
    engine->dsp_config.store(config.get(), std::memory_order_release);
    engine->dsp_configs.push_back(std::move(config));
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeDestroy(JNIEnv *, jclass, jlong value) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    if (engine == nullptr) return;
    destroy_engine(*engine);
    delete engine;
}

extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePrepare(JNIEnv *env, jclass, jlong value, jint fd, jfloat gain) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    std::string error;
    if (engine == nullptr || !open_output(*engine, error) || !open_slot(engine->slots[0], fd, engine->output_rate, gain, error)) {
        close(fd);
        throw_java(env, error.empty() ? "Player is closed" : error.c_str());
        return;
    }
    close(fd);
    release_slot(engine->slots[1]);
    engine->active_slot.store(0, std::memory_order_release);
    engine->preloaded_slot.store(kNoSlot, std::memory_order_release);
    engine->outgoing_slot.store(kNoSlot, std::memory_order_release);
    engine->output_disconnected.store(false, std::memory_order_release);
    engine->transition_event.store(kTransitionNone, std::memory_order_release);
}

extern "C" JNIEXPORT jboolean JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePreload(JNIEnv *env, jclass, jlong value, jint fd, jfloat gain) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    std::string error;
    if (engine == nullptr || engine->active_slot.load() == kNoSlot) {
        close(fd);
        throw_java(env, "Player is not prepared");
        return JNI_FALSE;
    }
    if (engine->preloaded_slot.load() != kNoSlot) {
        const int previous = engine->preloaded_slot.exchange(kNoSlot);
        wait_for_callback_quiescence(*engine);
        release_slot(engine->slots[previous]);
    }
    const int target = idle_slot_for_preload(*engine);
    wait_for_callback_quiescence(*engine);
    if (target == kNoSlot) {
        close(fd);
        throw_java(env, "No idle source slot");
        return JNI_FALSE;
    }
    // A retired source can still own its worker and FFmpeg allocations. Join
    // and clear it before placing the next preload in that slot; otherwise a
    // new std::thread assignment would terminate the process.
    release_slot(engine->slots[target]);
    if (!open_slot(engine->slots[target], fd, engine->output_rate, gain, error)) {
        close(fd);
        throw_java(env, error.empty() ? "Unable to preload source" : error.c_str());
        return JNI_FALSE;
    }
    close(fd);
    engine->preloaded_slot.store(target, std::memory_order_release);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeSetNormalizationGains(JNIEnv *, jclass, jlong value, jfloat active_gain, jfloat preloaded_gain) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    if (engine == nullptr) return;
    const int active = engine->active_slot.load(std::memory_order_acquire);
    if (active != kNoSlot) engine->slots[active].normalization_gain_db.store(active_gain, std::memory_order_release);
    const int preloaded = engine->preloaded_slot.load(std::memory_order_acquire);
    if (preloaded != kNoSlot) engine->slots[preloaded].normalization_gain_db.store(preloaded_gain, std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeClearPreloaded(JNIEnv *, jclass, jlong value) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    if (engine == nullptr) return;
    const int slot = engine->preloaded_slot.exchange(kNoSlot);
    if (slot != kNoSlot) {
        wait_for_callback_quiescence(*engine);
        release_slot(engine->slots[slot]);
    }
}

extern "C" JNIEXPORT jboolean JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeHasPreloaded(JNIEnv *, jclass, jlong value) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    return engine != nullptr && engine->preloaded_slot.load(std::memory_order_acquire) != kNoSlot;
}

extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeBeginCrossfade(JNIEnv *, jclass, jlong value, jlong duration_ms) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    if (engine == nullptr || engine->crossfading.load() || engine->preloaded_slot.load() == kNoSlot) return;
    const int incoming = engine->preloaded_slot.exchange(kNoSlot);
    const int outgoing = engine->active_slot.exchange(incoming);
    engine->outgoing_slot.store(outgoing, std::memory_order_release);
    engine->fade_frames_total.store(std::max<int64_t>(1, duration_ms * engine->output_rate / 1000), std::memory_order_release);
    engine->fade_frames_rendered.store(0, std::memory_order_release);
    engine->crossfading.store(true, std::memory_order_release);
    engine->transition_event.store(kTransitionCrossfadeStarted, std::memory_order_release);
}

extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeFinishCrossfade(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    if (e != nullptr) snap_fade(*e);
}
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeSnapCrossfade(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    if (e != nullptr) snap_fade(*e);
}
extern "C" JNIEXPORT jboolean JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeIsCrossfading(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    return e != nullptr && e->crossfading.load();
}
extern "C" JNIEXPORT jint JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeConsumeTransition(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    return e == nullptr ? kTransitionNone : e->transition_event.exchange(kTransitionNone);
}

extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeSetGlobalDspConfig(JNIEnv *env, jclass, jlong value, jfloat preamp, jfloat width, jfloatArray eq) {
    auto *engine = reinterpret_cast<PlaybackEngine *>(value);
    if (engine == nullptr) return;
    auto config = std::make_unique<GlobalDspConfig>();
    config->preamp_gain_db = preamp;
    config->stereo_width = width;
    if (eq != nullptr) {
        const jsize count = std::min<jsize>(env->GetArrayLength(eq), kEqBands);
        env->GetFloatArrayRegion(eq, 0, count, config->eq_band_gains_db);
    }
    engine->dsp_config.store(config.get(), std::memory_order_release);
    engine->dsp_configs.push_back(std::move(config));
}
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePlay(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    if (e != nullptr && e->stream != nullptr) {
        e->playing.store(true);
        AAudioStream_requestStart(e->stream);
    }
}
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePause(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    if (e != nullptr) {
        snap_fade(*e);
        e->playing.store(false);
        if (e->stream != nullptr) AAudioStream_requestPause(e->stream);
    }
}
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeStop(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    if (e != nullptr) {
        snap_fade(*e);
        e->playing.store(false);
        if (e->stream != nullptr) AAudioStream_requestPause(e->stream);
    }
}
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeSeekTo(JNIEnv *, jclass, jlong value, jlong ms) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    if (e == nullptr) return;
    snap_fade(*e);
    const int active = e->active_slot.load();
    if (active == kNoSlot) return;
    auto &s = e->slots[active];
    s.position_base_us.store(std::max<int64_t>(0, ms) * 1000);
    s.rendered_frames.store(0);
    s.seek_us.store(std::max<int64_t>(0, ms) * 1000);
    s.seek_pending.store(true);
}
extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeDurationMs(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    const int i = e == nullptr ? kNoSlot : e->active_slot.load();
    auto *s = i == kNoSlot ? nullptr : &e->slots[i];
    return s != nullptr && s->format != nullptr && s->format->duration > 0 ? s->format->duration / 1000 : 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePositionMs(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    const int i = e == nullptr ? kNoSlot : e->active_slot.load();
    if (i == kNoSlot) return 0;
    auto &s = e->slots[i];
    return (s.position_base_us.load() + av_rescale(s.rendered_frames.load(), AV_TIME_BASE, s.output_rate)) / 1000;
}
extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePreloadedDurationMs(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    const int i = e == nullptr ? kNoSlot : e->preloaded_slot.load();
    auto *s = i == kNoSlot ? nullptr : &e->slots[i];
    return s != nullptr && s->format != nullptr && s->format->duration > 0 ? s->format->duration / 1000 : 0;
}
extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePreloadedPositionMs(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    const int i = e == nullptr ? kNoSlot : e->preloaded_slot.load();
    if (i == kNoSlot) return 0;
    auto &s = e->slots[i];
    return (s.position_base_us.load() + av_rescale(s.rendered_frames.load(), AV_TIME_BASE, s.output_rate)) / 1000;
}
extern "C" JNIEXPORT jboolean JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeIsFinished(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    const int i = e == nullptr ? kNoSlot : e->active_slot.load();
    return i != kNoSlot && e->slots[i].finished.load();
}
extern "C" JNIEXPORT jboolean JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeIsOutputDisconnected(JNIEnv *, jclass, jlong value) {
    auto *e = reinterpret_cast<PlaybackEngine *>(value);
    return e != nullptr && e->output_disconnected.load();
}
