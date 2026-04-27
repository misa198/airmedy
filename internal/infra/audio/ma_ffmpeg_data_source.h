#ifndef MA_FFMPEG_DATA_SOURCE_H
#define MA_FFMPEG_DATA_SOURCE_H

#include "miniaudio/miniaudio.h"
#include "ffmpeg_decoder.h"

typedef struct {
    ma_data_source_base base;
    FFmpegHandle* ffmpeg;
} ma_ffmpeg_data_source;

static ma_result ma_ffmpeg_data_source_read(ma_data_source* pDataSource, void* pFramesOut, ma_uint64 frameCount, ma_uint64* pFramesRead) {
    ma_ffmpeg_data_source* pFFmpegDS = (ma_ffmpeg_data_source*)pDataSource;
    if (pFramesRead) *pFramesRead = 0;

    int read = ffmpeg_read(pFFmpegDS->ffmpeg, (float*)pFramesOut, (int)frameCount);
    if (read < 0) return MA_ERROR;
    
    if (pFramesRead) *pFramesRead = (ma_uint64)read;
    if (read == 0) return MA_AT_END;
    
    return MA_SUCCESS;
}

static ma_result ma_ffmpeg_data_source_seek(ma_data_source* pDataSource, ma_uint64 frameIndex) {
    ma_ffmpeg_data_source* pFFmpegDS = (ma_ffmpeg_data_source*)pDataSource;
    double seconds = (double)frameIndex / (double)pFFmpegDS->ffmpeg->target_rate;
    if (ffmpeg_seek(pFFmpegDS->ffmpeg, seconds) != 0) return MA_ERROR;
    return MA_SUCCESS;
}

static ma_result ma_ffmpeg_data_source_get_data_format(ma_data_source* pDataSource, ma_format* pFormat, ma_uint32* pChannels, ma_uint32* pSampleRate, ma_channel* pChannelMap, size_t channelMapCap) {
    ma_ffmpeg_data_source* pFFmpegDS = (ma_ffmpeg_data_source*)pDataSource;
    if (pFormat)     *pFormat     = ma_format_f32;
    if (pChannels)   *pChannels   = pFFmpegDS->ffmpeg->n_ch;
    if (pSampleRate) *pSampleRate = pFFmpegDS->ffmpeg->target_rate;
    if (pChannelMap) ma_channel_map_init_standard(ma_standard_channel_map_default, pChannelMap, ma_min(channelMapCap, pFFmpegDS->ffmpeg->n_ch), pFFmpegDS->ffmpeg->n_ch);
    return MA_SUCCESS;
}

static ma_result ma_ffmpeg_data_source_get_cursor(ma_data_source* pDataSource, ma_uint64* pCursor) {
    ma_ffmpeg_data_source* pFFmpegDS = (ma_ffmpeg_data_source*)pDataSource;
    if (pCursor) *pCursor = pFFmpegDS->ffmpeg->read_count;
    return MA_SUCCESS;
}

static ma_result ma_ffmpeg_data_source_get_length(ma_data_source* pDataSource, ma_uint64* pLength) {
    ma_ffmpeg_data_source* pFFmpegDS = (ma_ffmpeg_data_source*)pDataSource;
    if (pLength) *pLength = pFFmpegDS->ffmpeg->total_frames;
    return MA_SUCCESS;
}

static ma_result ma_ffmpeg_data_source_set_looping(ma_data_source* pDataSource, ma_bool32 isLooping) {
    (void)pDataSource;
    (void)isLooping;
    return MA_NOT_IMPLEMENTED;
}

static ma_data_source_vtable g_ma_ffmpeg_data_source_vtable = {
    ma_ffmpeg_data_source_read,
    ma_ffmpeg_data_source_seek,
    ma_ffmpeg_data_source_get_data_format,
    ma_ffmpeg_data_source_get_cursor,
    ma_ffmpeg_data_source_get_length,
    ma_ffmpeg_data_source_set_looping,
    0
};

static ma_result ma_ffmpeg_data_source_init(const char* path, ma_uint32 target_rate, ma_ffmpeg_data_source* pFFmpegDS) {
    ma_data_source_config config = ma_data_source_config_init();
    config.vtable = &g_ma_ffmpeg_data_source_vtable;

    ma_result result = ma_data_source_init(&config, &pFFmpegDS->base);
    if (result != MA_SUCCESS) return result;

    pFFmpegDS->ffmpeg = ffmpeg_open(path, target_rate);
    if (!pFFmpegDS->ffmpeg) return MA_INVALID_FILE;

    return MA_SUCCESS;
}

static void ma_ffmpeg_data_source_uninit(ma_ffmpeg_data_source* pFFmpegDS) {
    if (pFFmpegDS->ffmpeg) ffmpeg_close(pFFmpegDS->ffmpeg);
    ma_data_source_uninit(&pFFmpegDS->base);
}

#endif /* MA_FFMPEG_DATA_SOURCE_H */
