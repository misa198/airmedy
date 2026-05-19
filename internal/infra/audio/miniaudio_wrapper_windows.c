#define MA_NO_ENCODING
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio/miniaudio.h"
#include "miniaudio_wrapper.h"
#include "ma_ffmpeg_data_source.h"
#include <stdlib.h>
#include <windows.h>
#include <string.h>

/*
 * Ping-pong slot design: two sound/ffmpeg-ds slot pairs (slot_a, slot_b).
 * cur_* pointers always target the active slot; nxt_* target the idle slot.
 * On a gapless transition, we uninit cur, start nxt, then swap the pointers.
 * This keeps both ma_sound nodes at stable memory addresses, which is required
 * because miniaudio's node graph stores raw pointers internally.
 */
struct MaPlayer {
    ma_engine             engine;

    ma_sound              slot_a;
    ma_ffmpeg_data_source ffmpeg_a;
    ma_sound              slot_b;
    ma_ffmpeg_data_source ffmpeg_b;

    ma_sound*             cur_sound;
    ma_ffmpeg_data_source* cur_ffmpeg_ds;
    int                   cur_using_ffmpeg;
    int                   cur_loaded;

    ma_sound*             nxt_sound;
    ma_ffmpeg_data_source* nxt_ffmpeg_ds;
    int                   nxt_using_ffmpeg;
    int                   nxt_loaded;

    float                 volume;
    MaEndCallback         end_cb;
    void*                 end_userdata;
    ma_mutex              mu;

    ma_peak_node          eq_bands[10];
    int                   eq_enabled;
    float                 eq_gains[10];
};

static float g_eq_frequencies[] = {32.0f, 64.0f, 125.0f, 250.0f, 500.0f, 1000.0f, 2000.0f, 4000.0f, 8000.0f, 16000.0f};

static void internal_end_cb(void* userdata, ma_sound* pSound) {
    (void)pSound;
    MaPlayer* p = (MaPlayer*)userdata;
    if (p->end_cb) p->end_cb(p->end_userdata);
}

static void unload_cur_locked(MaPlayer* p) {
    if (p->cur_loaded) {
        ma_sound_uninit(p->cur_sound);
        p->cur_loaded = 0;
    }
    if (p->cur_using_ffmpeg) {
        ma_ffmpeg_data_source_uninit(p->cur_ffmpeg_ds);
        p->cur_using_ffmpeg = 0;
    }
}

static void unload_nxt_locked(MaPlayer* p) {
    if (p->nxt_loaded) {
        ma_sound_uninit(p->nxt_sound);
        p->nxt_loaded = 0;
    }
    if (p->nxt_using_ffmpeg) {
        ma_ffmpeg_data_source_uninit(p->nxt_ffmpeg_ds);
        p->nxt_using_ffmpeg = 0;
    }
}

static int load_into_slot(MaPlayer* p,
                          const char* path,
                          ma_sound* sound,
                          ma_ffmpeg_data_source* ffmpeg_ds,
                          int* out_using_ffmpeg,
                          int* out_loaded)
{
    ma_result sr;
    ma_uint32 engine_rate = ma_engine_get_sample_rate(&p->engine);
    if (engine_rate == 0) engine_rate = 44100;

    sr = ma_ffmpeg_data_source_init(path, engine_rate, ffmpeg_ds);
    if (sr != MA_SUCCESS) return (int)sr;
    *out_using_ffmpeg = 1;

    ma_sound_config config = ma_sound_config_init_2(&p->engine);
    config.pDataSource = ffmpeg_ds;
    config.channelsOut = 2;
    if (p->eq_enabled) {
        config.pInitialAttachment = (ma_node*)&p->eq_bands[0];
    }

    sr = ma_sound_init_ex(&p->engine, &config, sound);
    if (sr != MA_SUCCESS) {
        ma_ffmpeg_data_source_uninit(ffmpeg_ds);
        *out_using_ffmpeg = 0;
        return (int)sr;
    }
    *out_loaded = 1;
    return 0;
}

MaPlayer* ma_player_create(void) {
    MaPlayer* p = (MaPlayer*)calloc(1, sizeof(MaPlayer));
    if (!p) return NULL;
    if (ma_engine_init(NULL, &p->engine) != MA_SUCCESS) { free(p); return NULL; }
    ma_mutex_init(&p->mu);
    p->volume = 1.0f;

    p->cur_sound     = &p->slot_a;
    p->cur_ffmpeg_ds = &p->ffmpeg_a;
    p->nxt_sound     = &p->slot_b;
    p->nxt_ffmpeg_ds = &p->ffmpeg_b;

    ma_node* last_node = ma_engine_get_endpoint(&p->engine);
    for (int i = 9; i >= 0; i--) {
        ma_peak_node_config config = ma_peak_node_config_init(2, ma_engine_get_sample_rate(&p->engine), 0.0, 1.0, g_eq_frequencies[i]);
        if (ma_peak_node_init(ma_engine_get_node_graph(&p->engine), &config, NULL, &p->eq_bands[i]) == MA_SUCCESS) {
            ma_node_attach_output_bus(&p->eq_bands[i], 0, last_node, 0);
            last_node = (ma_node*)&p->eq_bands[i];
        }
        p->eq_gains[i] = 0.0f;
    }
    p->eq_enabled = 0;

    return p;
}

void ma_player_destroy(MaPlayer* p) {
    if (!p) return;
    ma_mutex_lock(&p->mu);
    unload_cur_locked(p);
    unload_nxt_locked(p);
    ma_mutex_unlock(&p->mu);
    for (int i = 0; i < 10; i++) {
        ma_peak_node_uninit(&p->eq_bands[i], NULL);
    }
    ma_engine_uninit(&p->engine);
    ma_mutex_uninit(&p->mu);
    free(p);
}

int ma_player_load(MaPlayer* p, const char* path) {
    if (!p || !path) return -1;
    ma_mutex_lock(&p->mu);
    unload_nxt_locked(p);
    unload_cur_locked(p);

    int r = load_into_slot(p, path,
                           p->cur_sound, p->cur_ffmpeg_ds,
                           &p->cur_using_ffmpeg, &p->cur_loaded);
    if (r == 0) {
        ma_sound_set_volume(p->cur_sound, p->volume);
        if (p->end_cb) ma_sound_set_end_callback(p->cur_sound, internal_end_cb, p);
    }
    ma_mutex_unlock(&p->mu);
    return r;
}

int ma_player_unload(MaPlayer* p) {
    if (!p) return -1;
    ma_mutex_lock(&p->mu);
    unload_cur_locked(p);
    ma_mutex_unlock(&p->mu);
    return 0;
}

int ma_player_play(MaPlayer* p) {
    if (!p || !p->cur_loaded) return -1;
    return (int)ma_sound_start(p->cur_sound);
}

int ma_player_pause(MaPlayer* p) {
    if (!p) return -1;
    if (!p->cur_loaded) return 0;
    return (int)ma_sound_stop(p->cur_sound);
}

int ma_player_stop(MaPlayer* p) {
    if (!p) return -1;
    if (!p->cur_loaded) return 0;
    ma_sound_stop(p->cur_sound);
    ma_sound_seek_to_pcm_frame(p->cur_sound, 0);
    return 0;
}

int ma_player_seek(MaPlayer* p, double seconds) {
    if (!p || !p->cur_loaded) return -1;
    ma_uint32 rate = ma_engine_get_sample_rate(&p->engine);
    if (p->cur_using_ffmpeg) {
        rate = p->cur_ffmpeg_ds->ffmpeg->target_rate;
    }
    ma_uint64 frame = (ma_uint64)(seconds * (double)rate);
    return (int)ma_sound_seek_to_pcm_frame(p->cur_sound, frame);
}

int ma_player_set_volume(MaPlayer* p, float volume) {
    if (!p) return -1;
    p->volume = volume;
    if (p->cur_loaded) ma_sound_set_volume(p->cur_sound, volume);
    return 0;
}

double ma_player_get_cursor(MaPlayer* p) {
    if (!p || !p->cur_loaded) return 0.0;
    ma_uint64 frames = 0;
    ma_sound_get_cursor_in_pcm_frames(p->cur_sound, &frames);
    ma_uint32 rate = ma_engine_get_sample_rate(&p->engine);
    if (p->cur_using_ffmpeg) rate = p->cur_ffmpeg_ds->ffmpeg->target_rate;
    return (double)frames / (double)rate;
}

double ma_player_get_length(MaPlayer* p) {
    if (!p || !p->cur_loaded) return 0.0;
    if (p->cur_using_ffmpeg) {
        return (double)p->cur_ffmpeg_ds->ffmpeg->total_frames /
               (double)p->cur_ffmpeg_ds->ffmpeg->target_rate;
    }
    float v = 0.0f;
    ma_sound_get_length_in_seconds(p->cur_sound, &v);
    return (double)v;
}

int ma_player_is_playing(MaPlayer* p) {
    if (!p || !p->cur_loaded) return 0;
    return ma_sound_is_playing(p->cur_sound) ? 1 : 0;
}

void ma_player_set_end_callback(MaPlayer* p, MaEndCallback cb, void* userdata) {
    if (!p) return;
    p->end_cb       = cb;
    p->end_userdata = userdata;
    if (p->cur_loaded && cb)
        ma_sound_set_end_callback(p->cur_sound, internal_end_cb, p);
}

int ma_player_set_eq_band(MaPlayer* p, int index, float frequency, float gain, float bandwidth) {
    if (!p || index < 0 || index >= 10) return -1;
    p->eq_gains[index] = gain;
    ma_peak_config config;
    config.format     = ma_format_f32;
    config.channels   = 2;
    config.sampleRate = ma_engine_get_sample_rate(&p->engine);
    config.frequency  = frequency;
    config.q          = bandwidth;
    config.gainDB     = gain;
    ma_peak_node_reinit(&config, &p->eq_bands[index]);
    return 0;
}

int ma_player_set_eq_enabled(MaPlayer* p, int enabled) {
    if (!p) return -1;
    ma_mutex_lock(&p->mu);
    p->eq_enabled = enabled;
    if (p->cur_loaded) {
        if (enabled) {
            ma_node_attach_output_bus(p->cur_sound, 0, &p->eq_bands[0], 0);
        } else {
            ma_node_attach_output_bus(p->cur_sound, 0, ma_engine_get_endpoint(&p->engine), 0);
        }
    }
    ma_mutex_unlock(&p->mu);
    return 0;
}

/* --- Gapless pre-loading --- */

int ma_player_preload_next(MaPlayer* p, const char* path) {
    if (!p || !path) return -1;
    ma_mutex_lock(&p->mu);
    unload_nxt_locked(p);
    int r = load_into_slot(p, path,
                           p->nxt_sound, p->nxt_ffmpeg_ds,
                           &p->nxt_using_ffmpeg, &p->nxt_loaded);
    if (r == 0) {
        ma_sound_set_volume(p->nxt_sound, p->volume);
    }
    ma_mutex_unlock(&p->mu);
    return r;
}

/*
 * Promote the pre-loaded next track to active and begin playback.
 * Must be called from a goroutine (NOT from the audio device thread) because
 * uninitializing a sound while inside the audio callback causes a deadlock.
 */
int ma_player_start_preloaded(MaPlayer* p) {
    if (!p) return -1;
    ma_mutex_lock(&p->mu);
    if (!p->nxt_loaded) {
        ma_mutex_unlock(&p->mu);
        return -1;
    }

    unload_cur_locked(p);

    ma_sound*             tmp_sound  = p->cur_sound;
    ma_ffmpeg_data_source* tmp_ffmpeg = p->cur_ffmpeg_ds;
    int                   tmp_using  = p->cur_using_ffmpeg;

    p->cur_sound        = p->nxt_sound;
    p->cur_ffmpeg_ds    = p->nxt_ffmpeg_ds;
    p->cur_using_ffmpeg = p->nxt_using_ffmpeg;
    p->cur_loaded       = 1;

    p->nxt_sound        = tmp_sound;
    p->nxt_ffmpeg_ds    = tmp_ffmpeg;
    p->nxt_using_ffmpeg = tmp_using;
    p->nxt_loaded       = 0;

    if (p->end_cb) ma_sound_set_end_callback(p->cur_sound, internal_end_cb, p);
    ma_result r = ma_sound_start(p->cur_sound);
    ma_mutex_unlock(&p->mu);
    return (r == MA_SUCCESS) ? 0 : (int)r;
}

void ma_player_clear_preloaded(MaPlayer* p) {
    if (!p) return;
    ma_mutex_lock(&p->mu);
    unload_nxt_locked(p);
    ma_mutex_unlock(&p->mu);
}
