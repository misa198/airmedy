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

    /* Initialize EQ bands in a chain: band[0] -> band[1] -> ... -> band[9] -> endpoint */
    ma_node* last_node = ma_engine_get_endpoint(&p->engine);
    for (int i = 9; i >= 0; i--) {
        ma_peak_node_config config = ma_peak_node_config_init(2, ma_engine_get_sample_rate(&p->engine), 0.0, 1.0, g_eq_frequencies[i]);
        if (ma_peak_node_init(ma_engine_get_node_graph(&p->engine), &config, NULL, &p->eq_bands[i]) != MA_SUCCESS) {
            /* Non-fatal */
        } else {
            ma_node_attach_output_bus(&p->eq_bands[i], 0, last_node, 0);
            last_node = (ma_node*)&p->eq_bands[i];
        }
        p->eq_gains[i] = 0.0f;
    }

    return p;
}

void ma_player_destroy(MaPlayer* p) {
    if (!p) return;
    ma_mutex_lock(&p->mu);
    unload_locked(p);
    ma_mutex_unlock(&p->mu);
    for (int i = 0; i < 10; i++) {
        ma_peak_node_uninit(&p->eq_bands[i], NULL);
    }
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

        ma_sound_config config = ma_sound_config_init_2(&p->engine);
        config.pDataSource = &p->ffmpeg_ds;
        config.channelsOut = 2; /* Always request stereo from engine to match downmix if needed */
        
        /* Route to EQ if enabled, otherwise direct to endpoint */
        if (p->eq_enabled) {
            config.pInitialAttachment = (ma_node*)&p->eq_bands[0];
        }

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

int ma_player_set_eq_band(MaPlayer* p, int index, float frequency, float gain, float bandwidth) {
    if (!p) return -1;
    if (index < 0 || index >= 10) return -1;
    
    p->eq_gains[index] = gain;
    
    ma_peak_config config;
    config.format = ma_format_f32;
    config.channels = 2;
    config.sampleRate = ma_engine_get_sample_rate(&p->engine);
    config.frequency = frequency;
    config.q = bandwidth;
    config.gainDB = gain;
    
    ma_peak_node_reinit(&config, &p->eq_bands[index]);
    return 0;
}

int ma_player_set_eq_enabled(MaPlayer* p, int enabled) {
    if (!p) return -1;
    ma_mutex_lock(&p->mu);
    p->eq_enabled = enabled;
    if (p->sound_loaded) {
        if (enabled) {
            ma_node_attach_output_bus(&p->sound, 0, &p->eq_bands[0], 0);
        } else {
            ma_node_attach_output_bus(&p->sound, 0, ma_engine_get_endpoint(&p->engine), 0);
        }
    }
    ma_mutex_unlock(&p->mu);
    return 0;
}
