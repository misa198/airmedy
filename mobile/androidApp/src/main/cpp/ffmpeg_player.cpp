#include <aaudio/AAudio.h>
#include <android/log.h>
#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <atomic>
#include <condition_variable>
#include <chrono>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/avutil.h>
#include <libavutil/channel_layout.h>
#include <libavutil/opt.h>
#include <libswresample/swresample.h>
}

namespace {
constexpr int kOutputChannels = 2;
constexpr size_t kRingFrames = 96'000;  // two seconds at 48 kHz

struct Player {
  AVFormatContext* format = nullptr;
  AVCodecContext* codec = nullptr;
  AVPacket* packet = nullptr;
  AVFrame* frame = nullptr;
  SwrContext* resampler = nullptr;
  AAudioStream* stream = nullptr;
  int audio_stream = -1;
  int output_rate = 48'000;
  std::vector<float> ring = std::vector<float>(kRingFrames * kOutputChannels);
  std::atomic<size_t> read_frame = 0;
  std::atomic<size_t> write_frame = 0;
  std::atomic<bool> playing = false;
  std::atomic<bool> stopping = false;
  std::atomic<bool> seek_pending = false;
  std::atomic<int64_t> seek_us = 0;
  std::atomic<int64_t> position_us = 0;
  std::atomic<bool> input_exhausted = false;
  std::atomic<bool> finished = false;
  std::thread decode_thread;
  std::mutex control_mutex;
};

void throw_java(JNIEnv* env, const char* message) {
  jclass exception = env->FindClass("java/lang/IllegalStateException");
  if (exception != nullptr) env->ThrowNew(exception, message);
}

size_t available(const Player& player) {
  const size_t read = player.read_frame.load(std::memory_order_acquire);
  const size_t write = player.write_frame.load(std::memory_order_acquire);
  return write >= read ? write - read : kRingFrames - read + write;
}

void push_samples(Player& player, const float* samples, size_t frames) {
  for (size_t i = 0; i < frames && !player.stopping.load(); ++i) {
    while (available(player) >= kRingFrames - 1 && !player.stopping.load()) std::this_thread::yield();
    const size_t write = player.write_frame.load(std::memory_order_relaxed);
    std::copy_n(samples + i * kOutputChannels, kOutputChannels, player.ring.data() + write * kOutputChannels);
    player.write_frame.store((write + 1) % kRingFrames, std::memory_order_release);
  }
}

aaudio_data_callback_result_t audio_callback(AAudioStream*, void* user, void* audio_data, int32_t frames) {
  auto& player = *static_cast<Player*>(user);
  auto* output = static_cast<float*>(audio_data);
  for (int32_t i = 0; i < frames; ++i) {
    const size_t read = player.read_frame.load(std::memory_order_relaxed);
    if (read == player.write_frame.load(std::memory_order_acquire) || !player.playing.load()) {
      output[i * kOutputChannels] = 0.0f;
      output[i * kOutputChannels + 1] = 0.0f;
      if (player.input_exhausted.load() && read == player.write_frame.load(std::memory_order_acquire)) {
        player.finished = true;
        player.playing = false;
      }
      continue;
    }
    output[i * kOutputChannels] = player.ring[read * kOutputChannels];
    output[i * kOutputChannels + 1] = player.ring[read * kOutputChannels + 1];
    player.read_frame.store((read + 1) % kRingFrames, std::memory_order_release);
  }
  return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void release_media(Player& player) {
  if (player.stream != nullptr) { AAudioStream_requestStop(player.stream); AAudioStream_close(player.stream); player.stream = nullptr; }
  swr_free(&player.resampler);
  av_frame_free(&player.frame);
  av_packet_free(&player.packet);
  avcodec_free_context(&player.codec);
  avformat_close_input(&player.format);
  player.audio_stream = -1;
}

bool open_media(Player& player, int fd, std::string& error) {
  const std::string path = "/proc/self/fd/" + std::to_string(fd);
  if (avformat_open_input(&player.format, path.c_str(), nullptr, nullptr) < 0 ||
      avformat_find_stream_info(player.format, nullptr) < 0) { error = "FFmpeg could not open this audio file"; return false; }
  player.audio_stream = av_find_best_stream(player.format, AVMEDIA_TYPE_AUDIO, -1, -1, nullptr, 0);
  if (player.audio_stream < 0) { error = "No audio stream in file"; return false; }
  const AVCodecParameters* parameters = player.format->streams[player.audio_stream]->codecpar;
  const AVCodec* decoder = avcodec_find_decoder(parameters->codec_id);
  if (decoder == nullptr) { error = "This FFmpeg build has no decoder for the audio stream"; return false; }
  player.codec = avcodec_alloc_context3(decoder);
  if (player.codec == nullptr || avcodec_parameters_to_context(player.codec, parameters) < 0 || avcodec_open2(player.codec, decoder, nullptr) < 0) { error = "FFmpeg could not initialize the decoder"; return false; }
  AAudioStreamBuilder* builder = nullptr;
  AAudio_createStreamBuilder(&builder);
  AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
  AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_FLOAT);
  AAudioStreamBuilder_setChannelCount(builder, kOutputChannels);
  AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
  AAudioStreamBuilder_setDataCallback(builder, audio_callback, &player);
  const aaudio_result_t output = AAudioStreamBuilder_openStream(builder, &player.stream);
  AAudioStreamBuilder_delete(builder);
  if (output != AAUDIO_OK) { error = "AAudio output is unavailable"; return false; }
  player.output_rate = AAudioStream_getSampleRate(player.stream);
  AVChannelLayout stereo = AV_CHANNEL_LAYOUT_STEREO;
  if (swr_alloc_set_opts2(&player.resampler, &stereo, AV_SAMPLE_FMT_FLT, player.output_rate,
                          &player.codec->ch_layout, player.codec->sample_fmt, player.codec->sample_rate, 0, nullptr) < 0 ||
      swr_init(player.resampler) < 0) { error = "FFmpeg could not configure PCM output"; return false; }
  player.packet = av_packet_alloc();
  player.frame = av_frame_alloc();
  return player.packet != nullptr && player.frame != nullptr;
}

void decoder_loop(Player& player) {
  std::vector<float> output;
  while (!player.stopping.load()) {
    if (!player.playing.load()) { std::this_thread::sleep_for(std::chrono::milliseconds(5)); continue; }
    if (player.seek_pending.exchange(false)) {
      const int64_t target = av_rescale_q(player.seek_us.load(), AVRational{1, AV_TIME_BASE}, player.format->streams[player.audio_stream]->time_base);
      av_seek_frame(player.format, player.audio_stream, target, AVSEEK_FLAG_BACKWARD);
      avcodec_flush_buffers(player.codec); player.read_frame = 0; player.write_frame = 0;
      player.input_exhausted = false; player.finished = false;
    }
    const int read = av_read_frame(player.format, player.packet);
    if (read < 0) { player.input_exhausted = true; std::this_thread::sleep_for(std::chrono::milliseconds(5)); continue; }
    if (player.packet->stream_index == player.audio_stream && avcodec_send_packet(player.codec, player.packet) >= 0) {
      while (avcodec_receive_frame(player.codec, player.frame) >= 0) {
        const int out_frames = swr_get_out_samples(player.resampler, player.frame->nb_samples);
        output.resize(static_cast<size_t>(out_frames) * kOutputChannels);
        uint8_t* out_data[] = { reinterpret_cast<uint8_t*>(output.data()) };
        const int converted = swr_convert(player.resampler, out_data, out_frames,
                                          const_cast<const uint8_t**>(player.frame->extended_data), player.frame->nb_samples);
        if (converted > 0) push_samples(player, output.data(), converted);
        if (player.frame->best_effort_timestamp != AV_NOPTS_VALUE) {
          player.position_us = av_rescale_q(player.frame->best_effort_timestamp, player.format->streams[player.audio_stream]->time_base, AVRational{1, AV_TIME_BASE});
        }
      }
    }
    av_packet_unref(player.packet);
  }
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeCreate(JNIEnv*, jclass) { return reinterpret_cast<jlong>(new Player()); }
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeDestroy(JNIEnv*, jclass, jlong value) {
  auto* player = reinterpret_cast<Player*>(value); if (player == nullptr) return;
  player->stopping = true; if (player->decode_thread.joinable()) player->decode_thread.join(); release_media(*player); delete player;
}
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePrepare(JNIEnv* env, jclass, jlong value, jint fd) {
  auto* player = reinterpret_cast<Player*>(value); std::string error;
  if (player == nullptr || !open_media(*player, fd, error)) { close(fd); throw_java(env, error.c_str()); return; }
  close(fd); player->stopping = false; player->input_exhausted = false; player->finished = false; player->decode_thread = std::thread(decoder_loop, std::ref(*player));
}
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePlay(JNIEnv*, jclass, jlong value) { auto* p = reinterpret_cast<Player*>(value); if (p != nullptr) { p->playing = true; AAudioStream_requestStart(p->stream); } }
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePause(JNIEnv*, jclass, jlong value) { auto* p = reinterpret_cast<Player*>(value); if (p != nullptr) p->playing = false; }
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeStop(JNIEnv*, jclass, jlong value) { auto* p = reinterpret_cast<Player*>(value); if (p != nullptr) { p->playing = false; p->read_frame = 0; p->write_frame = 0; } }
extern "C" JNIEXPORT void JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeSeekTo(JNIEnv*, jclass, jlong value, jlong ms) {
  auto* p = reinterpret_cast<Player*>(value);
  if (p != nullptr) {
    p->position_us = std::max<int64_t>(0, ms) * 1000;
    p->input_exhausted = false;
    p->finished = false;
    p->seek_us = ms * 1000;
    p->seek_pending = true;
  }
}
extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeDurationMs(JNIEnv*, jclass, jlong value) { auto* p = reinterpret_cast<Player*>(value); return p != nullptr && p->format != nullptr && p->format->duration > 0 ? p->format->duration / 1000 : 0; }
extern "C" JNIEXPORT jlong JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativePositionMs(JNIEnv*, jclass, jlong value) { auto* p = reinterpret_cast<Player*>(value); return p != nullptr ? p->position_us.load() / 1000 : 0; }
extern "C" JNIEXPORT jboolean JNICALL Java_me_misa198_airmedy_player_FfmpegDecoder_nativeIsFinished(JNIEnv*, jclass, jlong value) { auto* p = reinterpret_cast<Player*>(value); return p != nullptr && p->finished.load(); }
