#define MA_NO_ENCODING
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio/miniaudio.h"
#include "miniaudio_wrapper.h"
#include "ffmpeg_decoder.h"
#include <stdlib.h>
#include <windows.h>

struct MaPlayer {
    ma_engine          engine;
    ma_sound           sound;
    ma_audio_buffer_ref audio_buf;      /* PCM buffer ref — used when FFmpeg decoded */
    float*             pcm_data;        /* malloc'd PCM, freed on unload */
    ma_uint32          pcm_rate;        /* == engine sample rate when using FFmpeg */
    ma_uint64          pcm_frames;      /* total PCM frames in pcm_data */
    ma_uint32          pcm_channels;
    int                sound_loaded;
    int                using_buf;       /* 1 when backed by audio_buf instead of file */
    float              volume;
    MaEndCallback      end_cb;
    void*              end_userdata;
    ma_mutex           mu;
};

static void internal_end_cb(void* userdata, ma_sound* pSound) {
    (void)pSound;
    MaPlayer* p = (MaPlayer*)userdata;
    if (p->end_cb) p->end_cb(p->end_userdata);
}

static void unload_locked(MaPlayer* p) {
    if (p->sound_loaded) {
        ma_sound_uninit(&p->sound);
        p->sound_loaded = 0;
    }
    if (p->using_buf) {
        ma_audio_buffer_ref_uninit(&p->audio_buf);
        free(p->pcm_data);
        p->pcm_data    = NULL;
        p->pcm_frames  = 0;
        p->using_buf   = 0;
    }
}

MaPlayer* ma_player_create(void) {
    MaPlayer* p = (MaPlayer*)calloc(1, sizeof(MaPlayer));
    if (!p) return NULL;
    if (ma_engine_init(NULL, &p->engine) != MA_SUCCESS) { free(p); return NULL; }
    ma_mutex_init(&p->mu);
    p->volume = 1.0f;
    return p;
}

void ma_player_destroy(MaPlayer* p) {
    if (!p) return;
    ma_mutex_lock(&p->mu);
    unload_locked(p);
    ma_mutex_unlock(&p->mu);
    ma_engine_uninit(&p->engine);
    ma_mutex_uninit(&p->mu);
    free(p);
}

int ma_player_load(MaPlayer* p, const char* path) {
    if (!p || !path) return -1;
    ma_mutex_lock(&p->mu);
    unload_locked(p);

    /*
     * MinGW/Zig builds use plain fopen() (ANSI codepage) for ma_fopen.
     * Use the _w variant with a converted wide path so any Unicode filename works.
     */
    int wLen = MultiByteToWideChar(CP_UTF8, 0, path, -1, NULL, 0);
    if (wLen == 0) { ma_mutex_unlock(&p->mu); return (int)MA_INVALID_ARGS; }
    wchar_t* wPath = (wchar_t*)malloc(wLen * sizeof(wchar_t));
    if (!wPath) { ma_mutex_unlock(&p->mu); return (int)MA_OUT_OF_MEMORY; }
    MultiByteToWideChar(CP_UTF8, 0, path, -1, wPath, wLen);

    ma_result native = ma_sound_init_from_file_w(
        &p->engine, wPath, MA_SOUND_FLAG_DECODE, NULL, NULL, &p->sound);
    free(wPath);

    if (native == MA_SUCCESS) {
        p->sound_loaded = 1;
        goto done;
    }

    /* Native MiniAudio decoders don't support this format — fall back to FFmpeg */
    {
        ma_uint32 engine_rate = ma_engine_get_sample_rate(&p->engine);
        float*    pcm         = NULL;
        ma_uint64 frames      = 0;
        ma_uint32 channels    = 0;

        int ffr = ffmpeg_decode_file(path, engine_rate, &pcm, &frames, &channels);
        if (ffr != 0) { ma_mutex_unlock(&p->mu); return ffr; }

        ma_audio_buffer_ref_init(ma_format_f32, channels, pcm, frames, &p->audio_buf);
        /* Workaround: ma_audio_buffer_ref_init leaves sampleRate=0 in v0.11; set manually */
        p->audio_buf.sampleRate = engine_rate;

        p->pcm_data     = pcm;
        p->pcm_rate     = engine_rate;
        p->pcm_frames   = frames;
        p->pcm_channels = channels;
        p->using_buf    = 1;

        ma_result sr = ma_sound_init_from_data_source(&p->engine, &p->audio_buf, 0, NULL, &p->sound);
        if (sr != MA_SUCCESS) {
            ma_audio_buffer_ref_uninit(&p->audio_buf);
            free(pcm); p->pcm_data = NULL; p->using_buf = 0;
            ma_mutex_unlock(&p->mu);
            return (int)sr;
        }
        p->sound_loaded = 1;
    }

done:
    ma_sound_set_volume(&p->sound, p->volume);
    if (p->end_cb) ma_sound_set_end_callback(&p->sound, internal_end_cb, p);
    ma_mutex_unlock(&p->mu);
    return 0;
}

int ma_player_unload(MaPlayer* p) {
    if (!p) return -1;
    ma_mutex_lock(&p->mu);
    unload_locked(p);
    ma_mutex_unlock(&p->mu);
    return 0;
}

int ma_player_play(MaPlayer* p) {
    if (!p || !p->sound_loaded) return -1;
    return (int)ma_sound_start(&p->sound);
}

int ma_player_pause(MaPlayer* p) {
    if (!p || !p->sound_loaded) return -1;
    return (int)ma_sound_stop(&p->sound);
}

int ma_player_stop(MaPlayer* p) {
    if (!p || !p->sound_loaded) return -1;
    ma_sound_stop(&p->sound);
    ma_sound_seek_to_pcm_frame(&p->sound, 0);
    return 0;
}

int ma_player_seek(MaPlayer* p, double seconds) {
    if (!p || !p->sound_loaded) return -1;
    ma_uint64 frame;
    if (p->using_buf && p->pcm_rate > 0)
        frame = (ma_uint64)(seconds * (double)p->pcm_rate);
    else
        frame = (ma_uint64)(seconds * (double)ma_engine_get_sample_rate(&p->engine));
    return (int)ma_sound_seek_to_pcm_frame(&p->sound, frame);
}

int ma_player_set_volume(MaPlayer* p, float volume) {
    if (!p) return -1;
    p->volume = volume;
    if (p->sound_loaded) ma_sound_set_volume(&p->sound, volume);
    return 0;
}

double ma_player_get_cursor(MaPlayer* p) {
    if (!p || !p->sound_loaded) return 0.0;
    if (p->using_buf && p->pcm_rate > 0) {
        ma_uint64 frames = 0;
        ma_sound_get_cursor_in_pcm_frames(&p->sound, &frames);
        return (double)frames / (double)p->pcm_rate;
    }
    float v = 0.0f;
    ma_sound_get_cursor_in_seconds(&p->sound, &v);
    return (double)v;
}

double ma_player_get_length(MaPlayer* p) {
    if (!p || !p->sound_loaded) return 0.0;
    if (p->using_buf && p->pcm_rate > 0)
        return (double)p->pcm_frames / (double)p->pcm_rate;
    float v = 0.0f;
    ma_sound_get_length_in_seconds(&p->sound, &v);
    return (double)v;
}

int ma_player_is_playing(MaPlayer* p) {
    if (!p || !p->sound_loaded) return 0;
    return ma_sound_is_playing(&p->sound) ? 1 : 0;
}

void ma_player_set_end_callback(MaPlayer* p, MaEndCallback cb, void* userdata) {
    if (!p) return;
    p->end_cb       = cb;
    p->end_userdata = userdata;
    if (p->sound_loaded && cb)
        ma_sound_set_end_callback(&p->sound, internal_end_cb, p);
}
