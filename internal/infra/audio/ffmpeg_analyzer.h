/*
 * ffmpeg_analyzer.h — single-header in-process loudness/dynamics/spectral analyzer.
 *
 * Decodes a file once through an
 *   abuffer -> ebur128 -> aspectralstats -> astats -> abuffersink
 * libavfilter graph and drains the per-frame filter metadata into an
 * FFAnalysisResult (loudness, dynamics, spectral features). No binary is
 * shipped: this links the static ffmpeg_libs archives directly.
 *
 * Mirrors the open/probe logic of ffmpeg_decoder.h. Each call builds and tears
 * down its own graph + codec context (nothing is shared), and forces
 * thread_count = 1 so an outer worker pool drives parallelism.
 */
#ifndef FFMPEG_ANALYZER_H
#define FFMPEG_ANALYZER_H

#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/opt.h>
#include <libavutil/dict.h>
#include <libavutil/channel_layout.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersrc.h>
#include <libavfilter/buffersink.h>
#include <libswresample/swresample.h>

#include <aubio/aubio.h>
#include <pthread.h>

#include "ffmpeg_shared_mu.h"

/* aubio tempo runs on a fixed mono rate so BPM math is rate-independent. */
#define FFA_TEMPO_RATE 44100
#define FFA_TEMPO_BUF  1024
#define FFA_TEMPO_HOP  512

/*
 * aubio is built here with --disable-fftw3/--disable-accelerate, so it falls
 * back to its bundled ooura FFT, which keeps mutable state in static/global
 * work buffers and is NOT reentrant. The rest of this file (avcodec/avformat/
 * avfilter) is safe to run concurrently from a worker pool, but every aubio
 * entry point (new_/del_/do_ for both tempo and onset) must be serialized
 * process-wide or concurrent calls corrupt the heap (observed as
 * "malloc: *** error ... pointer being freed was not allocated" -> SIGABRT
 * under a multi-worker analysis pool).
 */
static pthread_mutex_t ffa_aubio_mu = PTHREAD_MUTEX_INITIALIZER;

/*
 * Covers avformat_open_input .. avcodec_open2 (format probing + codec open).
 * Probing an embedded MJPEG cover-art stream (very common in mp3 files) can
 * trigger a decoder's one-time lazy static-table init (VLC/huffman tables);
 * under concurrent first-touch from a worker pool this vendored ffmpeg build
 * has corrupted the heap (SIGABRT via malloc). The actual decode/filter loop
 * below is safely reentrant per AVFormatContext/AVCodecContext, so only the
 * open+probe phase is serialized here.
 *
 * This must also be serialized against ffmpeg_decoder.h's playback opens
 * (analysis and playback run concurrently), so the lock lives in
 * ffmpeg_shared_mu.h/.c with external linkage rather than as a `static`
 * mutex local to this translation unit.
 */

/* Numeric features, all as doubles for a simple cgo bridge. */
typedef struct {
    double loudness_lufs;     /* ebur128 integrated loudness (LUFS)            */
    double loudness_range;    /* ebur128 loudness range (LU)                   */
    double true_peak;         /* max true peak across channels (dBFS)          */
    double rms;               /* astats overall RMS level (dBFS)               */
    double crest;             /* astats overall crest factor (linear ratio)    */
    double spectral_centroid; /* aspectralstats centroid (Hz), mean over frames*/
    double spectral_rolloff;  /* aspectralstats rolloff (Hz), mean             */
    double spectral_flatness; /* aspectralstats flatness, mean                 */
    double spectral_flux;     /* aspectralstats flux, mean                     */
    double zcr;               /* astats overall zero-crossings rate            */
    double tempo;             /* estimated tempo (BPM) via aubio, 0 if unknown */
    double onset_variance;    /* inter-onset-interval variance via aubio_onset */
} FFAnalysisResult;

enum {
    FFA_OK            = 0,
    FFA_ERR_OPEN      = -1,  /* could not open / find audio stream             */
    FFA_ERR_DECODER   = -2,  /* could not open a decoder                       */
    FFA_ERR_GRAPH     = -3,  /* could not build the filter graph               */
    FFA_ERR_PROCESS   = -4,  /* fatal error while decoding/filtering           */
    FFA_ERR_CANCELLED = -5,  /* aborted via *cancel                            */
    FFA_ERR_ALLOC     = -6,  /* allocation failure                             */
};

static int ffa_get_meta(AVFrame *f, const char *key, double *val) {
    AVDictionaryEntry *e = av_dict_get(f->metadata, key, NULL, 0);
    if (!e) return 0;
    *val = atof(e->value);
    return 1;
}

/*
 * ffa_feed_tempo_onset resamples one decoded frame to mono float @ FFA_TEMPO_RATE
 * and pushes the samples into aubio_tempo + aubio_onset in hop-sized chunks
 * (both consume the same resampled buffer — no extra decode/resample pass),
 * accumulating a BPM estimate on each detected beat and an inter-onset-interval
 * variance via Welford's running-variance algorithm on each detected onset.
 * No-op if tempo detection is disabled (tempo-related pointers NULL); onset
 * detection is independently optional (may be NULL while tempo still runs).
 * Non-fatal: on resample failure it simply skips the frame.
 */
static void ffa_feed_tempo_onset(AVFrame *frame, SwrContext *swr, float **swr_out, int *swr_cap,
                           aubio_tempo_t *tempo, fvec_t *in, fvec_t *out, float *buf, int *fill,
                           double *bpm_sum, long *bpm_count,
                           aubio_onset_t *onset, fvec_t *onset_out,
                           long *onset_total_samples, long *onset_last_pos,
                           long *onset_count, double *onset_mean, double *onset_M2) {
    if (!swr || !tempo || !in || !out || !buf) return;

    int max_out = swr_get_out_samples(swr, frame->nb_samples) + 256;
    if (max_out <= 0) return;
    if (max_out > *swr_cap) {
        float *nb = (float *)realloc(*swr_out, sizeof(float) * max_out);
        if (!nb) return;
        *swr_out = nb;
        *swr_cap = max_out;
    }

    uint8_t *dst[1] = { (uint8_t *)*swr_out };
    int got = swr_convert(swr, dst, max_out, (const uint8_t **)frame->data, frame->nb_samples);
    for (int i = 0; i < got; i++) {
        buf[(*fill)++] = (*swr_out)[i];
        if (*fill == FFA_TEMPO_HOP) {
            memcpy(in->data, buf, sizeof(smpl_t) * FFA_TEMPO_HOP);
            pthread_mutex_lock(&ffa_aubio_mu);
            aubio_tempo_do(tempo, in, out);
            if (out->data[0] != 0) { /* a beat was detected this hop */
                double b = (double)aubio_tempo_get_bpm(tempo);
                if (b > 0) { *bpm_sum += b; (*bpm_count)++; }
            }
            if (onset && onset_out) {
                aubio_onset_do(onset, in, onset_out);
                /* aubio's peak-picker reports a spurious onset during warm-up
                 * (HFC has no prior history yet) — ignore hits before the
                 * analysis window has filled once. */
                if (onset_out->data[0] != 0 && *onset_total_samples >= FFA_TEMPO_BUF) {
                    long pos = *onset_total_samples;
                    if (*onset_last_pos >= 0) {
                        double interval = (double)(pos - *onset_last_pos);
                        (*onset_count)++;
                        double delta = interval - *onset_mean;
                        *onset_mean += delta / (double)(*onset_count);
                        *onset_M2 += delta * (interval - *onset_mean);
                    }
                    *onset_last_pos = pos;
                }
                *onset_total_samples += FFA_TEMPO_HOP;
            }
            pthread_mutex_unlock(&ffa_aubio_mu);
            *fill = 0;
        }
    }
}

/*
 * ffmpeg_analyze decodes path and fills *out. *cancel (may be NULL) is polled in
 * the work loop; setting it non-zero aborts with FFA_ERR_CANCELLED. Returns
 * FFA_OK (0) on success or a negative FFA_ERR_* code.
 */
static int ffmpeg_analyze(const char *path, FFAnalysisResult *out, volatile int *cancel) {
    if (!path || !out) return FFA_ERR_OPEN;
    memset(out, 0, sizeof(*out));

    AVFormatContext *fmt_ctx = NULL;
    AVCodecContext  *codec_ctx = NULL;
    AVFilterGraph   *graph = NULL;
    AVFilterContext *src_ctx = NULL, *sink_ctx = NULL;
    AVFilterInOut   *gin = NULL, *gout = NULL;
    AVPacket        *pkt = NULL;
    AVFrame         *frame = NULL, *filt = NULL;
    int rc = FFA_ERR_PROCESS;
    int stream_idx = -1;
    int probe_locked = 0; /* guards ffmpeg_probe_mu; see comment at its declaration */

    /* tempo (BPM) detection via aubio, fed mono float PCM resampled by swr */
    SwrContext   *tempo_swr = NULL;
    aubio_tempo_t *tempo = NULL;
    fvec_t       *tempo_in = NULL, *tempo_out = NULL;
    float        *tempo_buf = NULL;  /* accumulates samples up to one hop */
    int           tempo_fill = 0;
    float        *swr_out = NULL;    /* reusable swr output buffer */
    int           swr_out_cap = 0;
    double        bpm_sum = 0;       /* mean of per-beat BPM estimates */
    long          bpm_count = 0;

    /* onset detection (rhythm regularity) via aubio, sharing tempo's resampled
     * buffer/hop cadence. Welford's running-variance algorithm avoids storing
     * every onset timestamp for long tracks. */
    aubio_onset_t *onset = NULL;
    fvec_t        *onset_out = NULL;
    long           onset_total_samples = 0;
    long           onset_last_pos = -1;    /* -1 = no onset seen yet */
    long           onset_count = 0;        /* number of intervals seen */
    double         onset_mean = 0;
    double         onset_M2 = 0;           /* sum of squared deviations from mean */

    /* spectral accumulators (mean over frames) + peak running maxima */
    double sp_centroid = 0, sp_rolloff = 0, sp_flatness = 0, sp_flux = 0;
    long   sp_count = 0;
    double tp_lin = 0;     /* ebur128 true peak, LINEAR amplitude, running max  */
    int    have_true_peak = 0;
    double peak_db = 0;    /* astats sample peak (dBFS), running max, fallback   */
    int    have_peak_db = 0;

    pthread_mutex_lock(&ffmpeg_probe_mu);
    probe_locked = 1;

    AVDictionary *format_opts = NULL;
    av_dict_set(&format_opts, "probesize", "32000000", 0);
    av_dict_set(&format_opts, "analyzeduration", "10000000", 0);
    if (avformat_open_input(&fmt_ctx, path, NULL, &format_opts) < 0) {
        av_dict_free(&format_opts);
        rc = FFA_ERR_OPEN;
        goto done;
    }
    av_dict_free(&format_opts);

    avformat_find_stream_info(fmt_ctx, NULL);
    for (unsigned int i = 0; i < fmt_ctx->nb_streams; i++) {
        if (fmt_ctx->streams[i]->codecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
            fmt_ctx->streams[i]->discard = AVDISCARD_ALL;
        }
    }

    const AVCodec *codec = NULL;
    stream_idx = av_find_best_stream(fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, &codec, 0);
    if (stream_idx < 0 || !codec) { rc = FFA_ERR_OPEN; goto done; }

    codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx) { rc = FFA_ERR_ALLOC; goto done; }
    avcodec_parameters_to_context(codec_ctx, fmt_ctx->streams[stream_idx]->codecpar);
    codec_ctx->thread_count = 1; /* pool concurrency drives throughput, not intra-decode */
    if (avcodec_open2(codec_ctx, codec, NULL) < 0) { rc = FFA_ERR_DECODER; goto done; }
    avcodec_flush_buffers(codec_ctx);

    pthread_mutex_unlock(&ffmpeg_probe_mu);
    probe_locked = 0;

    if (codec_ctx->sample_rate == 0) { rc = FFA_ERR_DECODER; goto done; }

    /* ---- build the filter graph ---- */
    graph = avfilter_graph_alloc();
    if (!graph) { rc = FFA_ERR_ALLOC; goto done; }

    {
        char ch_layout[256];
#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(59, 37, 100)
        AVChannelLayout in_layout;
        if (codec_ctx->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC) {
            av_channel_layout_default(&in_layout, codec_ctx->ch_layout.nb_channels > 0
                                                      ? codec_ctx->ch_layout.nb_channels : 2);
        } else {
            av_channel_layout_copy(&in_layout, &codec_ctx->ch_layout);
        }
        av_channel_layout_describe(&in_layout, ch_layout, sizeof(ch_layout));
        av_channel_layout_uninit(&in_layout);
#else
        int64_t mask = codec_ctx->channel_layout
                           ? codec_ctx->channel_layout
                           : av_get_default_channel_layout(codec_ctx->channels > 0 ? codec_ctx->channels : 2);
        av_get_channel_layout_string(ch_layout, sizeof(ch_layout), 0, mask);
#endif

        char args[512];
        snprintf(args, sizeof(args),
                 "time_base=1/%d:sample_rate=%d:sample_fmt=%s:channel_layout=%s",
                 codec_ctx->sample_rate, codec_ctx->sample_rate,
                 av_get_sample_fmt_name(codec_ctx->sample_fmt), ch_layout);

        const AVFilter *abuffer = avfilter_get_by_name("abuffer");
        const AVFilter *abuffersink = avfilter_get_by_name("abuffersink");
        if (!abuffer || !abuffersink) { rc = FFA_ERR_GRAPH; goto done; }

        if (avfilter_graph_create_filter(&src_ctx, abuffer, "in", args, NULL, graph) < 0) {
            rc = FFA_ERR_GRAPH; goto done;
        }
        if (avfilter_graph_create_filter(&sink_ctx, abuffersink, "out", NULL, NULL, graph) < 0) {
            rc = FFA_ERR_GRAPH; goto done;
        }

        gout = avfilter_inout_alloc();
        gin = avfilter_inout_alloc();
        if (!gout || !gin) { rc = FFA_ERR_ALLOC; goto done; }
        gout->name = av_strdup("in");
        gout->filter_ctx = src_ctx;
        gout->pad_idx = 0;
        gout->next = NULL;
        gin->name = av_strdup("out");
        gin->filter_ctx = sink_ctx;
        gin->pad_idx = 0;
        gin->next = NULL;

        /* ebur128 emits I/LRA + true peak; aspectralstats centroid/rolloff/flatness/flux;
         * astats RMS/crest/ZCR. aresample/aformat are auto-inserted as needed. */
        const char *desc =
            "ebur128=metadata=1:peak=true,aspectralstats=measure=centroid+rolloff+flatness+flux,astats=metadata=1:reset=0";
        if (avfilter_graph_parse_ptr(graph, desc, &gin, &gout, NULL) < 0) {
            rc = FFA_ERR_GRAPH; goto done;
        }
        if (avfilter_graph_config(graph, NULL) < 0) { rc = FFA_ERR_GRAPH; goto done; }
    }

    /* ---- tempo (BPM): swr to mono float @ FFA_TEMPO_RATE -> aubio_tempo ----
     * Tempo failures are non-fatal: leave out->tempo = 0 and keep analyzing. */
    {
#if LIBAVCODEC_VERSION_INT >= AV_VERSION_INT(59, 37, 100)
        AVChannelLayout in_l, out_l;
        if (codec_ctx->ch_layout.order == AV_CHANNEL_ORDER_UNSPEC) {
            av_channel_layout_default(&in_l, codec_ctx->ch_layout.nb_channels > 0 ? codec_ctx->ch_layout.nb_channels : 2);
        } else {
            av_channel_layout_copy(&in_l, &codec_ctx->ch_layout);
        }
        av_channel_layout_default(&out_l, 1);
        if (swr_alloc_set_opts2(&tempo_swr, &out_l, AV_SAMPLE_FMT_FLT, FFA_TEMPO_RATE,
                                &in_l, codec_ctx->sample_fmt, codec_ctx->sample_rate, 0, NULL) < 0) {
            tempo_swr = NULL;
        }
        av_channel_layout_uninit(&in_l);
        av_channel_layout_uninit(&out_l);
#else
        tempo_swr = swr_alloc();
        if (tempo_swr) {
            int64_t inl = codec_ctx->channel_layout ? codec_ctx->channel_layout
                            : av_get_default_channel_layout(codec_ctx->channels > 0 ? codec_ctx->channels : 2);
            av_opt_set_int(tempo_swr, "in_channel_layout", inl, 0);
            av_opt_set_int(tempo_swr, "in_sample_rate", codec_ctx->sample_rate, 0);
            av_opt_set_sample_fmt(tempo_swr, "in_sample_fmt", codec_ctx->sample_fmt, 0);
            av_opt_set_int(tempo_swr, "out_channel_layout", AV_CH_LAYOUT_MONO, 0);
            av_opt_set_int(tempo_swr, "out_sample_rate", FFA_TEMPO_RATE, 0);
            av_opt_set_sample_fmt(tempo_swr, "out_sample_fmt", AV_SAMPLE_FMT_FLT, 0);
        }
#endif
        if (tempo_swr && swr_init(tempo_swr) < 0) { swr_free(&tempo_swr); tempo_swr = NULL; }

        if (tempo_swr) {
            pthread_mutex_lock(&ffa_aubio_mu);
            tempo = new_aubio_tempo("default", FFA_TEMPO_BUF, FFA_TEMPO_HOP, FFA_TEMPO_RATE);
            tempo_in = new_fvec(FFA_TEMPO_HOP);
            tempo_out = new_fvec(2);
            tempo_buf = (float *)malloc(sizeof(float) * FFA_TEMPO_HOP);
            if (!tempo || !tempo_in || !tempo_out || !tempo_buf) {
                /* tempo unavailable — clean up partial state, continue without BPM */
                if (tempo) del_aubio_tempo(tempo);
                if (tempo_in) del_fvec(tempo_in);
                if (tempo_out) del_fvec(tempo_out);
                free(tempo_buf);
                swr_free(&tempo_swr);
                tempo = NULL; tempo_in = NULL; tempo_out = NULL; tempo_buf = NULL; tempo_swr = NULL;
            }
            pthread_mutex_unlock(&ffa_aubio_mu);
        }

        /* ---- onset detection: reuses tempo's swr + hop buffer, independent of
         * tempo's own success/failure. Non-fatal: leave out->onset_variance = 0. */
        if (tempo_swr) {
            pthread_mutex_lock(&ffa_aubio_mu);
            onset = new_aubio_onset("hfc", FFA_TEMPO_BUF, FFA_TEMPO_HOP, FFA_TEMPO_RATE);
            onset_out = new_fvec(1);
            if (!onset || !onset_out) {
                if (onset) del_aubio_onset(onset);
                if (onset_out) del_fvec(onset_out);
                onset = NULL; onset_out = NULL;
            }
            pthread_mutex_unlock(&ffa_aubio_mu);
        }
    }

    pkt = av_packet_alloc();
    frame = av_frame_alloc();
    filt = av_frame_alloc();
    if (!pkt || !frame || !filt) { rc = FFA_ERR_ALLOC; goto done; }

    /* ---- decode -> filter -> drain metadata ---- */
    {
        int eof = 0, flushed = 0;
        double last_lufs = 0, last_lra = 0, last_rms = 0, last_crest = 0, last_zcr = 0;
        int have_loudness = 0, have_astats = 0;

        while (!eof) {
            if (cancel && *cancel) { rc = FFA_ERR_CANCELLED; goto done; }

            int ret = avcodec_receive_frame(codec_ctx, frame);
            if (ret == 0) {
                if (av_buffersrc_add_frame_flags(src_ctx, frame, AV_BUFFERSRC_FLAG_KEEP_REF) < 0) {
                    av_frame_unref(frame);
                    rc = FFA_ERR_PROCESS; goto done;
                }
                ffa_feed_tempo_onset(frame, tempo_swr, &swr_out, &swr_out_cap,
                               tempo, tempo_in, tempo_out, tempo_buf, &tempo_fill,
                               &bpm_sum, &bpm_count,
                               onset, onset_out,
                               &onset_total_samples, &onset_last_pos,
                               &onset_count, &onset_mean, &onset_M2);
                av_frame_unref(frame);
            } else if (ret == AVERROR(EAGAIN)) {
                if (flushed) {
                    /* decoder drained: signal EOS to the graph and drain it below */
                    (void)av_buffersrc_add_frame_flags(src_ctx, NULL, 0);
                    eof = 1;
                } else if (av_read_frame(fmt_ctx, pkt) >= 0) {
                    if (pkt->stream_index == stream_idx) {
                        int s = avcodec_send_packet(codec_ctx, pkt);
                        if (s < 0 && s != AVERROR(EAGAIN)) { av_packet_unref(pkt); rc = FFA_ERR_PROCESS; goto done; }
                    }
                    av_packet_unref(pkt);
                } else {
                    avcodec_send_packet(codec_ctx, NULL);
                    flushed = 1;
                }
            } else if (ret == AVERROR_EOF) {
                (void)av_buffersrc_add_frame_flags(src_ctx, NULL, 0);
                eof = 1;
            } else {
                rc = FFA_ERR_PROCESS; goto done;
            }

            /* pull whatever the sink has ready */
            for (;;) {
                int fr = av_buffersink_get_frame(sink_ctx, filt);
                if (fr == AVERROR(EAGAIN) || fr == AVERROR_EOF) break;
                if (fr < 0) { rc = FFA_ERR_PROCESS; goto done; }

                double v;
                if (ffa_get_meta(filt, "lavfi.r128.I", &v))   { last_lufs = v; have_loudness = 1; }
                if (ffa_get_meta(filt, "lavfi.r128.LRA", &v)) { last_lra = v; }
                /* ebur128 true peak is reported per channel as a LINEAR amplitude
                 * (e.g. 0.5 == -6 dBFS); keep the running max and convert at the end. */
                for (int ch = 0; ch < 8; ch++) {
                    char key[48];
                    snprintf(key, sizeof(key), "lavfi.r128.true_peaks_ch%d", ch);
                    if (ffa_get_meta(filt, key, &v)) {
                        if (!have_true_peak || v > tp_lin) { tp_lin = v; have_true_peak = 1; }
                    }
                }

                /* astats: Overall.* carries cumulative RMS; crest/ZCR are per-channel
                 * only (no Overall key), so read channel 1. reset=0 keeps them cumulative. */
                if (ffa_get_meta(filt, "lavfi.astats.Overall.RMS_level", &v))   { last_rms = v; have_astats = 1; }
                if (ffa_get_meta(filt, "lavfi.astats.1.Crest_factor", &v))      { last_crest = v; }
                if (ffa_get_meta(filt, "lavfi.astats.1.Zero_crossings_rate", &v)) { last_zcr = v; }
                /* astats sample peak (dBFS) — fallback when ebur128 true peak is absent */
                if (ffa_get_meta(filt, "lavfi.astats.Overall.Peak_level", &v)) {
                    if (!have_peak_db || v > peak_db) { peak_db = v; have_peak_db = 1; }
                }

                if (ffa_get_meta(filt, "lavfi.aspectralstats.1.centroid", &v)) { sp_centroid += v; }
                if (ffa_get_meta(filt, "lavfi.aspectralstats.1.rolloff", &v))  { sp_rolloff += v; }
                if (ffa_get_meta(filt, "lavfi.aspectralstats.1.flatness", &v)) { sp_flatness += v; }
                if (ffa_get_meta(filt, "lavfi.aspectralstats.1.flux", &v))     { sp_flux += v; sp_count++; }

                av_frame_unref(filt);
            }
        }

        if (!have_loudness && !have_astats) { rc = FFA_ERR_PROCESS; goto done; }

        out->loudness_lufs = last_lufs;
        out->loudness_range = last_lra;
        if (have_true_peak && tp_lin > 0) {
            out->true_peak = 20.0 * log10(tp_lin); /* linear -> dBFS */
        } else if (have_peak_db) {
            out->true_peak = peak_db;              /* astats sample peak (dBFS) */
        } else {
            out->true_peak = 0;
        }
        out->rms = last_rms;
        out->crest = last_crest;
        out->zcr = last_zcr;
        if (sp_count > 0) {
            out->spectral_centroid = sp_centroid / (double)sp_count;
            out->spectral_rolloff = sp_rolloff / (double)sp_count;
            out->spectral_flatness = sp_flatness / (double)sp_count;
            out->spectral_flux = sp_flux / (double)sp_count;
        }
        /* Mean of the per-beat BPM estimates; 0 when no stable beat was found. */
        out->tempo = (bpm_count > 0) ? (bpm_sum / (double)bpm_count) : 0;
        /* Sample variance of inter-onset intervals; 0 when too few onsets to measure. */
        out->onset_variance = (onset_count > 1) ? (onset_M2 / (double)(onset_count - 1)) : 0;
        rc = FFA_OK;
    }

done:
    if (probe_locked) pthread_mutex_unlock(&ffmpeg_probe_mu);
    pthread_mutex_lock(&ffa_aubio_mu);
    if (onset) del_aubio_onset(onset);
    if (onset_out) del_fvec(onset_out);
    if (tempo) del_aubio_tempo(tempo);
    if (tempo_in) del_fvec(tempo_in);
    if (tempo_out) del_fvec(tempo_out);
    pthread_mutex_unlock(&ffa_aubio_mu);
    free(tempo_buf);
    free(swr_out);
    if (tempo_swr) swr_free(&tempo_swr);
    if (filt) av_frame_free(&filt);
    if (frame) av_frame_free(&frame);
    if (pkt) av_packet_free(&pkt);
    if (gin) avfilter_inout_free(&gin);
    if (gout) avfilter_inout_free(&gout);
    if (graph) avfilter_graph_free(&graph);
    if (codec_ctx) avcodec_free_context(&codec_ctx);
    if (fmt_ctx) avformat_close_input(&fmt_ctx);
    return rc;
}

#endif /* FFMPEG_ANALYZER_H */
