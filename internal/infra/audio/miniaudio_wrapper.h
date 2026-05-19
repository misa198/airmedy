#ifndef MINIAUDIO_WRAPPER_H
#define MINIAUDIO_WRAPPER_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct MaPlayer MaPlayer;
typedef void (*MaEndCallback)(void* userdata);

/* Lifecycle */
MaPlayer* ma_player_create(void);
void      ma_player_destroy(MaPlayer* p);

/* File operations */
int ma_player_load(MaPlayer* p, const char* path);
int ma_player_unload(MaPlayer* p);

/* Transport */
int ma_player_play(MaPlayer* p);
int ma_player_pause(MaPlayer* p);
int ma_player_stop(MaPlayer* p);

/* Seek — position in seconds */
int ma_player_seek(MaPlayer* p, double seconds);

/* Volume: 0.0–1.0 */
int ma_player_set_volume(MaPlayer* p, float volume);

/* Queries */
double ma_player_get_cursor(MaPlayer* p);
double ma_player_get_length(MaPlayer* p);
int    ma_player_is_playing(MaPlayer* p);

/* EQ: index 0-9, frequency in Hz, gain in dB, bandwidth (Q) */
int ma_player_set_eq_band(MaPlayer* p, int index, float frequency, float gain, float bandwidth);
int ma_player_set_eq_enabled(MaPlayer* p, int enabled);

/* Track-end callback — fired from MiniAudio device thread, must not block */
void ma_player_set_end_callback(MaPlayer* p, MaEndCallback cb, void* userdata);

/* Gapless: pre-load next track while current plays */
int  ma_player_preload_next(MaPlayer* p, const char* path);
/* Gapless: promote pre-loaded track to active and start it (call from goroutine, not audio thread) */
int  ma_player_start_preloaded(MaPlayer* p);
/* Gapless: discard pre-loaded track without playing */
void ma_player_clear_preloaded(MaPlayer* p);

#ifdef __cplusplus
}
#endif

#endif /* MINIAUDIO_WRAPPER_H */
