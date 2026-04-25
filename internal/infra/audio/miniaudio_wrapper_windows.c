#define MA_NO_ENCODING
#define MINIAUDIO_IMPLEMENTATION
#include "miniaudio/miniaudio.h"
#include "miniaudio_wrapper.h"
#include <stdlib.h>

struct MaPlayer {
    ma_engine     engine;
    ma_sound      sound;
    int           sound_loaded;
    float         volume;
    MaEndCallback end_cb;
    void*         end_userdata;
    ma_mutex      mu;
};

static void internal_end_cb(void* userdata, ma_sound* pSound) {
    (void)pSound;
    MaPlayer* p = (MaPlayer*)userdata;
    if (p->end_cb) {
        p->end_cb(p->end_userdata);
    }
}

MaPlayer* ma_player_create(void) {
    MaPlayer* p = (MaPlayer*)calloc(1, sizeof(MaPlayer));
    if (!p) return NULL;

    if (ma_engine_init(NULL, &p->engine) != MA_SUCCESS) {
        free(p);
        return NULL;
    }
    ma_mutex_init(&p->mu);
    p->volume = 1.0f;
    return p;
}

void ma_player_destroy(MaPlayer* p) {
    if (!p) return;
    ma_mutex_lock(&p->mu);
    if (p->sound_loaded) {
        ma_sound_uninit(&p->sound);
        p->sound_loaded = 0;
    }
    ma_mutex_unlock(&p->mu);
    ma_engine_uninit(&p->engine);
    ma_mutex_uninit(&p->mu);
    free(p);
}

int ma_player_load(MaPlayer* p, const char* path) {
    if (!p || !path) return -1;
    ma_mutex_lock(&p->mu);

    if (p->sound_loaded) {
        ma_sound_uninit(&p->sound);
        p->sound_loaded = 0;
    }

    ma_result result = ma_sound_init_from_file(
        &p->engine, path, MA_SOUND_FLAG_DECODE, NULL, NULL, &p->sound);
    if (result != MA_SUCCESS) {
        ma_mutex_unlock(&p->mu);
        return (int)result;
    }

    ma_sound_set_volume(&p->sound, p->volume);
    if (p->end_cb) {
        ma_sound_set_end_callback(&p->sound, internal_end_cb, p);
    }
    p->sound_loaded = 1;
    ma_mutex_unlock(&p->mu);
    return 0;
}

int ma_player_unload(MaPlayer* p) {
    if (!p) return -1;
    ma_mutex_lock(&p->mu);
    if (p->sound_loaded) {
        ma_sound_uninit(&p->sound);
        p->sound_loaded = 0;
    }
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
    ma_uint32 sr = ma_engine_get_sample_rate(&p->engine);
    ma_uint64 frame = (ma_uint64)(seconds * (double)sr);
    return (int)ma_sound_seek_to_pcm_frame(&p->sound, frame);
}

int ma_player_set_volume(MaPlayer* p, float volume) {
    if (!p) return -1;
    p->volume = volume;
    if (p->sound_loaded) {
        ma_sound_set_volume(&p->sound, volume);
    }
    return 0;
}

double ma_player_get_cursor(MaPlayer* p) {
    if (!p || !p->sound_loaded) return 0.0;
    float cursor = 0.0f;
    ma_sound_get_cursor_in_seconds(&p->sound, &cursor);
    return (double)cursor;
}

double ma_player_get_length(MaPlayer* p) {
    if (!p || !p->sound_loaded) return 0.0;
    float length = 0.0f;
    ma_sound_get_length_in_seconds(&p->sound, &length);
    return (double)length;
}

int ma_player_is_playing(MaPlayer* p) {
    if (!p || !p->sound_loaded) return 0;
    return ma_sound_is_playing(&p->sound) ? 1 : 0;
}

void ma_player_set_end_callback(MaPlayer* p, MaEndCallback cb, void* userdata) {
    if (!p) return;
    p->end_cb = cb;
    p->end_userdata = userdata;
    if (p->sound_loaded && cb) {
        ma_sound_set_end_callback(&p->sound, internal_end_cb, p);
    }
}
