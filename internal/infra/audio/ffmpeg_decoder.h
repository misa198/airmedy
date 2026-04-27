/*
 * ffmpeg_decoder.h — single-header FFmpeg decode-to-PCM helper.
 *
 * Supports streaming (section-by-section) decoding to interleaved float32 PCM.
 * Decodes any FFmpeg-supported format (including DSF/DSD) and resamples to target_rate.
 * Automatically downmixes multi-channel audio to stereo for compatibility.
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

typedef struct {
    AVFormatContext* fmt_ctx;
    AVCodecContext*  codec_ctx;
    SwrContext*      swr;
    int              stream_idx;
    AVPacket*        pkt;
    AVFrame*         frame;
    ma_uint32        target_rate;
    ma_uint32        n_ch;        /* Output channels (max 2) */
    ma_uint64        total_frames;
    ma_uint64        read_count;  /* Total frames read so far */
    int              is_eof;
    int              flushed;     /* Sent NULL to avcodec_send_packet */
    
    /* Leftover buffer for handling partial frames from swr_convert */
    float*           leftover_buf;
    int              leftover_count;
    int              leftover_offset;
    int              leftover_cap;

    /* Reusable buffer for swr output */
    float*           swr_tmp_buf;
    int              swr_tmp_cap;
} FFmpegHandle;

static void ffmpeg_close(FFmpegHandle* h) {
    if (!h) return;
    if (h->frame) av_frame_free(&h->frame);
    if (h->pkt)   av_packet_free(&h->pkt);
    if (h->swr)   swr_free(&h->swr);
    if (h->codec_ctx) avcodec_free_context(&h->codec_ctx);
    if (h->fmt_ctx)   avformat_close_input(&h->fmt_ctx);
    if (h->leftover_buf) free(h->leftover_buf);
    if (h->swr_tmp_buf)  free(h->swr_tmp_buf);
    free(h);
}

static FFmpegHandle* ffmpeg_open(const char* path, ma_uint32 target_rate) {
    FFmpegHandle* h = (FFmpegHandle*)calloc(1, sizeof(FFmpegHandle));
    if (!h) return NULL;
    h->target_rate = target_rate;

    AVDictionary* format_opts = NULL;
    av_dict_set(&format_opts, "probesize", "32000000", 0);      /* 32MB */
    av_dict_set(&format_opts, "analyzeduration", "10000000", 0); /* 10s */

    if (avformat_open_input(&h->fmt_ctx, path, NULL, &format_opts) < 0) {
        av_dict_free(&format_opts);
        free(h);
        return NULL;
    }
    av_dict_free(&format_opts);

    /* 
     * avformat_find_stream_info can return errors for files with "broken" metadata streams
     * (like timescale errors in M4A), but the audio stream might be perfectly fine.
     * We proceed even on error and let av_find_best_stream be the final arbiter.
     */
    avformat_find_stream_info(h->fmt_ctx, NULL);

    /* Discard non-audio streams AFTER probing to ensure demuxer has all info it needs */
    for (unsigned int i = 0; i < h->fmt_ctx->nb_streams; i++) {
        if (h->fmt_ctx->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
            h->fmt_ctx->streams[i]->discard = AVDISCARD_ALL;
        }
    }

    const AVCodec* codec = NULL;
    h->stream_idx = av_find_best_stream(h->fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
    if (h->stream_idx < 0 || !codec) {
        ffmpeg_close(h);
        return NULL;
    }

    h->codec_ctx = avcodec_alloc_context3(codec);
    if (!h->codec_ctx) {
        ffmpeg_close(h);
        return NULL;
    }

    avcodec_parameters_to_context(h->codec_ctx, h->fmt_ctx->streams[h->stream_idx]->codecpar);
    if (avcodec_open2(h->codec_ctx, codec, NULL) < 0) {
        ffmpeg_close(h);
        return NULL;
    }
    avcodec_flush_buffers(h->codec_ctx);

    ma_uint32 in_ch;
#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(59, 37, 100)
    in_ch = (ma_uint32)h->codec_ctx->ch_layout.nb_channels;
#else
    in_ch = (ma_uint32)h->codec_ctx->channels;
#endif

    if (in_ch == 0 || h->codec_ctx->sample_rate == 0) {
        ffmpeg_close(h);
        return NULL;
    }

    /* Force stereo downmix for multi-channel files (ensures compatibility with stereo-only pipelines) */
    h->n_ch = (in_ch > 2) ? 2 : in_ch;

#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(59, 37, 100)
    {
        AVChannelLayout in_ch_layout;
        if (h->codec_ctx->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC) {
            av_channel_layout_default(&in_ch_layout, (int)in_ch);
        } else {
            av_channel_layout_copy(&in_ch_layout, &h->codec_ctx->ch_layout);
        }
        AVChannelLayout out_ch_layout;
        av_channel_layout_default(&out_ch_layout, (int)h->n_ch);
        swr_alloc_set_opts2(&h->swr, &out_ch_layout, AV_SAMPLE_FMT_FLT, (int)target_rate,
                            &in_ch_layout, h->codec_ctx->sample_fmt, h->codec_ctx->sample_rate, 0, NULL);
        av_channel_layout_uninit(&in_ch_layout);
        av_channel_layout_uninit(&out_ch_layout);
    }
#else
    {
        h->swr = swr_alloc();
        int64_t in_layout = h->codec_ctx->channel_layout ? h->codec_ctx->channel_layout : av_get_default_channel_layout((int)in_ch);
        int64_t out_layout = av_get_default_channel_layout((int)h->n_ch);
        av_opt_set_int(h->swr, "in_channel_layout", in_layout, 0);
        av_opt_set_int(h->swr, "in_sample_rate", h->codec_ctx->sample_rate, 0);
        av_opt_set_sample_fmt(h->swr, "in_sample_fmt", h->codec_ctx->sample_fmt, 0);
        av_opt_set_int(h->swr, "out_channel_layout", out_layout, 0);
        av_opt_set_int(h->swr, "out_sample_rate", (int)target_rate, 0);
        av_opt_set_sample_fmt(h->swr, "out_sample_fmt", AV_SAMPLE_FMT_FLT, 0);
    }
#endif

    if (!h->swr || swr_init(h->swr) < 0) {
        ffmpeg_close(h);
        return NULL;
    }

    AVStream* st = h->fmt_ctx->streams[h->stream_idx];
    if (st->duration != AV_NOPTS_VALUE) {
        h->total_frames = (ma_uint64)((double)st->duration * st->time_base.num / st->time_base.den * target_rate);
    } else if (h->fmt_ctx->duration != AV_NOPTS_VALUE) {
        h->total_frames = (ma_uint64)((double)h->fmt_ctx->duration / AV_TIME_BASE * target_rate);
    }

    h->pkt = av_packet_alloc();
    h->frame = av_frame_alloc();
    h->leftover_cap = 16384;
    h->leftover_buf = (float*)malloc(h->leftover_cap * h->n_ch * sizeof(float));
    h->swr_tmp_cap = 16384;
    h->swr_tmp_buf = (float*)malloc(h->swr_tmp_cap * h->n_ch * sizeof(float));
    
    if (!h->pkt || !h->frame || !h->leftover_buf || !h->swr_tmp_buf) {
        ffmpeg_close(h);
        return NULL;
    }
    
    return h;
}

static int ffmpeg_read(FFmpegHandle* h, float* buffer, int frames_requested) {
    if (!h || !buffer || frames_requested <= 0) return 0;
    int frames_filled = 0;
    
    /* 1. Pull from leftover buffer if we have data from a previous decode */
    if (h->leftover_count > 0) {
        int to_copy = (h->leftover_count < frames_requested) ? h->leftover_count : frames_requested;
        memcpy(buffer, h->leftover_buf + h->leftover_offset * h->n_ch, to_copy * h->n_ch * sizeof(float));
        h->leftover_count -= to_copy;
        h->leftover_offset += to_copy;
        frames_filled += to_copy;
    }

    /* 2. Decode more frames as needed */
    while (frames_filled < frames_requested && !h->is_eof) {
        av_frame_unref(h->frame);
        int ret = avcodec_receive_frame(h->codec_ctx, h->frame);
        
        if (ret == 0) {
            int max_out = swr_get_out_samples(h->swr, h->frame->nb_samples) + 256;
            if (max_out > h->swr_tmp_cap) {
                h->swr_tmp_cap = max_out;
                h->swr_tmp_buf = (float*)realloc(h->swr_tmp_buf, h->swr_tmp_cap * h->n_ch * sizeof(float));
            }
            uint8_t* dst[1] = { (uint8_t*)h->swr_tmp_buf };
            int got = swr_convert(h->swr, dst, max_out, (const uint8_t**)h->frame->data, h->frame->nb_samples);
            if (got > 0) {
                int needed = frames_requested - frames_filled;
                int to_copy = (got < needed) ? got : needed;
                memcpy(buffer + frames_filled * h->n_ch, h->swr_tmp_buf, to_copy * h->n_ch * sizeof(float));
                frames_filled += to_copy;
                if (got > to_copy) {
                    int rem = got - to_copy;
                    if (rem > h->leftover_cap) {
                        h->leftover_cap = rem + 4096;
                        h->leftover_buf = (float*)realloc(h->leftover_buf, h->leftover_cap * h->n_ch * sizeof(float));
                    }
                    memcpy(h->leftover_buf, h->swr_tmp_buf + to_copy * h->n_ch, rem * h->n_ch * sizeof(float));
                    h->leftover_count = rem;
                    h->leftover_offset = 0;
                }
            }
            continue;
        } 
        
        if (ret == AVERROR_EOF) {
            h->is_eof = 1;
            break;
        }

        if (ret == AVERROR(EAGAIN)) {
            if (h->flushed) {
                h->is_eof = 1;
                break;
            }
            if (av_read_frame(h->fmt_ctx, h->pkt) >= 0) {
                if (h->pkt->stream_index == h->stream_idx) {
                    int send_ret = avcodec_send_packet(h->codec_ctx, h->pkt);
                    if (send_ret < 0 && send_ret != AVERROR(EAGAIN)) {
                        h->is_eof = 1; /* Fatal error */
                    }
                }
                av_packet_unref(h->pkt);
            } else {
                avcodec_send_packet(h->codec_ctx, NULL);
                h->flushed = 1;
            }
            continue;
        }
        
        h->is_eof = 1;
        break;
    }

    h->read_count += frames_filled;
    return frames_filled;
}

static int ffmpeg_seek(FFmpegHandle* h, double seconds) {
    if (!h) return -1;
    int64_t ts = (int64_t)(seconds / av_q2d(h->fmt_ctx->streams[h->stream_idx]->time_base));
    if (avformat_seek_file(h->fmt_ctx, h->stream_idx, INT64_MIN, ts, ts, 0) < 0) {
        return -1;
    }
    avcodec_flush_buffers(h->codec_ctx);
    swr_init(h->swr);
    h->is_eof = 0;
    h->flushed = 0;
    h->leftover_count = 0;
    h->leftover_offset = 0;
    h->read_count = (ma_uint64)(seconds * h->target_rate);
    return 0;
}

/* Legacy wrapper for compatibility with static whole-file decoding */
static int ffmpeg_decode_file(
    const char* path,
    ma_uint32   target_rate,
    float**     out_pcm,
    ma_uint64*  out_frames,
    ma_uint32*  out_channels)
{
    FFmpegHandle* h = ffmpeg_open(path, target_rate);
    if (!h) return -10;

    ma_uint64 capacity = h->total_frames > 0 ? (h->total_frames + 8192) * h->n_ch : (ma_uint64)target_rate * 60 * h->n_ch;
    float* pcm = (float*)malloc(capacity * sizeof(float));
    if (!pcm) { ffmpeg_close(h); return -4; }
    
    ma_uint64 filled = 0;
    float buf[8192 * 2];
    int n;
    while ((n = ffmpeg_read(h, buf, 8192)) > 0) {
        if (filled + n * h->n_ch > capacity) {
            capacity = (filled + n * h->n_ch) * 2;
            float* new_pcm = (float*)realloc(pcm, capacity * sizeof(float));
            if (!new_pcm) { free(pcm); ffmpeg_close(h); return -4; }
            pcm = new_pcm;
        }
        memcpy(pcm + filled, buf, n * h->n_ch * sizeof(float));
        filled += n * h->n_ch;
    }
    
    *out_pcm = pcm;
    *out_frames = filled / h->n_ch;
    *out_channels = h->n_ch;
    ffmpeg_close(h);
    return 0;
}

#endif /* FFMPEG_DECODER_H */
