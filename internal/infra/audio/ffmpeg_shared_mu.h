/*
 * ffmpeg_shared_mu.h — mutex shared across every cgo translation unit that
 * opens/probes files through the vendored ffmpeg build.
 *
 * ffmpeg_analyzer.h and ffmpeg_decoder.h are each pulled into a *different*
 * cgo preamble (analyzer.go vs player_miniaudio.go), so a `static` mutex
 * declared inside either header would give each translation unit its own
 * independent lock — useless for cross-file serialization. This header
 * declares one mutex with external linkage, defined once in
 * ffmpeg_shared_mu.c, so the analysis worker pool and the playback decoder
 * actually contend on the same lock during avformat_open_input .. avcodec_open2
 * (the phase that can trigger a decoder's lazy static-table init and corrupt
 * the heap under concurrent first-touch).
 */
#ifndef FFMPEG_SHARED_MU_H
#define FFMPEG_SHARED_MU_H

#include <pthread.h>

extern pthread_mutex_t ffmpeg_probe_mu;

#endif /* FFMPEG_SHARED_MU_H */
