#define MA_NO_ENCODING
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio/miniaudio.h"
#include "miniaudio_wrapper.h"
#include "ma_ffmpeg_data_source.h"
#include <stdlib.h>
#include <windows.h>

struct MaPlayer {
    ma_engine             engine;
    ma_sound              sound;
    ma_ffmpeg_data_source ffmpeg_ds;
    int                   sound_loaded;
    int                   using_ffmpeg;
    float                 volume;
    MaEndCallback         end_cb;
    void*                 end_userdata;
    ma_mutex              mu;
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
    if (p->using_ffmpeg) {
        ma_ffmpeg_data_source_uninit(&p->ffmpeg_ds);
        p->using_ffmpeg = 0;
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
    ma_result sr;
    if (!p || !path) return -1;
    ma_mutex_lock(&p->mu);
    unload_locked(p);

    /* 
     * Refactor: Always use FFmpeg as the decoding backend for all formats.
     * This provides consistent behavior and robustness across Windows and Linux,
     * while miniaudio remains the playback engine and controller.
     */
    {
        ma_uint32 engine_rate = ma_engine_get_sample_rate(&p->engine);
        if (engine_rate == 0) {
            engine_rate = 44100; /* Fallback for headless or uninitialized device states */
        }

        sr = ma_ffmpeg_data_source_init(path, engine_rate, &p->ffmpeg_ds);
        if (sr != MA_SUCCESS) {
            ma_mutex_unlock(&p->mu);
            return (int)sr;
        }
        p->using_ffmpeg = 1;

        ma_sound_config config = ma_sound_config_init();
        config.pDataSource = &p->ffmpeg_ds;
        config.channelsOut = 2; /* Always request stereo from engine to match downmix if needed */

        sr = ma_sound_init_ex(&p->engine, &config, &p->sound);
        if (sr != MA_SUCCESS) {
            ma_ffmpeg_data_source_uninit(&p->ffmpeg_ds);
            p->using_ffmpeg = 0;
            ma_mutex_unlock(&p->mu);
            return (int)sr;
        }

        p->sound_loaded = 1;
    }

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
    if (!p) return -1;
    if (!p->sound_loaded) return 0;
    return (int)ma_sound_stop(&p->sound);
}

int ma_player_stop(MaPlayer* p) {
    if (!p) return -1;
    if (!p->sound_loaded) return 0;
    ma_sound_stop(&p->sound);
    ma_sound_seek_to_pcm_frame(&p->sound, 0);
    return 0;
}

int ma_player_seek(MaPlayer* p, double seconds) {
    if (!p || !p->sound_loaded) return -1;
    ma_uint32 rate = ma_engine_get_sample_rate(&p->engine);
    if (p->using_ffmpeg) {
        rate = p->ffmpeg_ds.ffmpeg->target_rate;
    }
    ma_uint64 frame = (ma_uint64)(seconds * (double)rate);
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
    ma_uint64 frames = 0;
    ma_sound_get_cursor_in_pcm_frames(&p->sound, &frames);
    
    ma_uint32 rate = ma_engine_get_sample_rate(&p->engine);
    if (p->using_ffmpeg) {
        rate = p->ffmpeg_ds.ffmpeg->target_rate;
    }
    return (double)frames / (double)rate;
}

double ma_player_get_length(MaPlayer* p) {
    if (!p || !p->sound_loaded) return 0.0;
    ma_uint32 rate = ma_engine_get_sample_rate(&p->engine);
    if (p->using_ffmpeg) {
        return (double)p->ffmpeg_ds.ffmpeg->total_frames / (double)p->ffmpeg_ds.ffmpeg->target_rate;
    }
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
