/*
 * ffmpeg_decoder.h — single-header FFmpeg decode-to-PCM helper.
 *
 * Included (as a static-function library) by each OS-specific wrapper file.
 * Decodes any FFmpeg-supported format to interleaved float32 PCM resampled to
 * `target_rate`, matching the MiniAudio engine's native sample rate so
 * ma_audio_buffer_ref can be used directly without further resampling.
 *
 * Supports FFmpeg 4.x (old channel-layout API) and 5.1+ (new AVChannelLayout).
 */
#ifndef FFMPEG_DECODER_H
#define FFMPEG_DECODER_H

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/opt.h>
#include <libavutil/channel_layout.h>
#include <libswresample/swresample.h>

typedef unsigned int           ma_uint32;
typedef unsigned long long     ma_uint64;

/*
 * Decode the audio file at UTF-8 `path` to interleaved float32 PCM.
 * The output is resampled to `target_rate` Hz.
 *
 * On success returns 0 and *out_pcm is malloc'd (caller must free).
 * On failure returns a negative value (miniaudio error code convention):
 *   -7  MA_DOES_NOT_EXIST  — file not found / can't open
 *   -10 MA_INVALID_FILE    — no audio stream / unsupported codec
 *   -4  MA_OUT_OF_MEMORY
 *   -1  MA_ERROR           — resampler init failed
 */
static int ffmpeg_decode_file(
    const char* path,
    ma_uint32   target_rate,
    float**     out_pcm,
    ma_uint64*  out_frames,
    ma_uint32*  out_channels)
{
    AVFormatContext* fmt_ctx = NULL;
    if (avformat_open_input(&fmt_ctx, path, NULL, NULL) < 0)
        return -7;

    if (avformat_find_stream_info(fmt_ctx, NULL) < 0) {
        avformat_close_input(&fmt_ctx);
        return -10;
    }

    const AVCodec* codec = NULL;
    int stream_idx = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
    if (stream_idx < 0 || !codec) {
        avformat_close_input(&fmt_ctx);
        return -10;
    }

    AVCodecContext* codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx) { avformat_close_input(&fmt_ctx); return -4; }

    avcodec_parameters_to_context(codec_ctx, fmt_ctx->streams[stream_idx]->codecpar);
    if (avcodec_open2(codec_ctx, codec, NULL) < 0) {
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt_ctx);
        return -10;
    }

#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(59, 37, 100)
    ma_uint32 n_ch = (ma_uint32)codec_ctx->ch_layout.nb_channels;
#else
    ma_uint32 n_ch = (ma_uint32)codec_ctx->channels;
#endif

    if (n_ch == 0 || codec_ctx->sample_rate == 0) {
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt_ctx);
        return -10;
    }

    SwrContext* swr = NULL;

#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(59, 37, 100)
    {
        AVChannelLayout out_ch_layout;
        av_channel_layout_default(&out_ch_layout, (int)n_ch);
        swr_alloc_set_opts2(&swr,
            &out_ch_layout,        AV_SAMPLE_FMT_FLT, (int)target_rate,
            &codec_ctx->ch_layout, codec_ctx->sample_fmt, codec_ctx->sample_rate,
            0, NULL);
        av_channel_layout_uninit(&out_ch_layout);
    }
#else
    {
        swr = swr_alloc();
        int64_t in_layout = codec_ctx->channel_layout
                              ? codec_ctx->channel_layout
                              : av_get_default_channel_layout((int)n_ch);
        av_opt_set_int(swr,        "in_channel_layout",  in_layout,               0);
        av_opt_set_int(swr,        "in_sample_rate",      codec_ctx->sample_rate,  0);
        av_opt_set_sample_fmt(swr, "in_sample_fmt",       codec_ctx->sample_fmt,   0);
        av_opt_set_int(swr,        "out_channel_layout",  av_get_default_channel_layout((int)n_ch), 0);
        av_opt_set_int(swr,        "out_sample_rate",    (int)target_rate,          0);
        av_opt_set_sample_fmt(swr, "out_sample_fmt",      AV_SAMPLE_FMT_FLT,       0);
    }
#endif

    if (!swr || swr_init(swr) < 0) {
        if (swr) swr_free(&swr);
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt_ctx);
        return -1;
    }

    /* Pre-allocate based on estimated duration */
    AVStream*  st       = fmt_ctx->streams[stream_idx];
    ma_uint64  capacity = (ma_uint64)target_rate * 60 * n_ch; /* 60s default */
    if (st->duration != AV_NOPTS_VALUE && st->time_base.den > 0) {
        double    dur_s = (double)st->duration * st->time_base.num / st->time_base.den;
        ma_uint64 est   = (ma_uint64)(dur_s * target_rate * n_ch) + n_ch * 8192;
        if (est > capacity) capacity = est;
    }

    float*    pcm    = (float*)malloc(capacity * sizeof(float));
    ma_uint64 filled = 0; /* samples filled (frames * channels) */

    AVPacket* pkt   = av_packet_alloc();
    AVFrame*  frame = av_frame_alloc();

    while (av_read_frame(fmt_ctx, pkt) >= 0) {
        if (pkt->stream_index != stream_idx) { av_packet_unref(pkt); continue; }

        if (avcodec_send_packet(codec_ctx, pkt) >= 0) {
            while (avcodec_receive_frame(codec_ctx, frame) == 0) {
                /* +256 headroom for resampler latency */
                int       max_out = frame->nb_samples + 256;
                ma_uint64 needed  = filled + (ma_uint64)max_out * n_ch;
                if (needed > capacity) { capacity = needed * 2; pcm = (float*)realloc(pcm, capacity * sizeof(float)); }

                uint8_t* dst[1] = { (uint8_t*)(pcm + filled) };
                int got = swr_convert(swr, dst, max_out,
                                      (const uint8_t**)frame->data, frame->nb_samples);
                if (got > 0) filled += (ma_uint64)got * n_ch;
                av_frame_unref(frame);
            }
        }
        av_packet_unref(pkt);
    }

    /* Flush resampler */
    for (;;) {
        ma_uint64 needed = filled + 4096 * n_ch;
        if (needed > capacity) { capacity = needed * 2; pcm = (float*)realloc(pcm, capacity * sizeof(float)); }
        uint8_t* dst[1] = { (uint8_t*)(pcm + filled) };
        int got = swr_convert(swr, dst, 4096, NULL, 0);
        if (got <= 0) break;
        filled += (ma_uint64)got * n_ch;
    }

    av_frame_free(&frame);
    av_packet_free(&pkt);
    swr_free(&swr);
    avcodec_free_context(&codec_ctx);
    avformat_close_input(&fmt_ctx);

    *out_pcm      = pcm;
    *out_frames   = (n_ch > 0) ? filled / n_ch : 0;
    *out_channels = n_ch;
    return 0;
}

#endif /* FFMPEG_DECODER_H */
