/*
 * keyfinder_bridge.h — C-ABI bridge around libkeyfinder's C++ API.
 *
 * Implemented in keyfinder_bridge.cpp (compiled by CXX via cgo, same pattern as
 * smtc_windows.cpp/.h). ffmpeg_analyzer.h (a plain-C header compiled into the
 * cgo preamble) calls these functions directly from its decode loop, feeding the
 * same mono float @ FFA_TEMPO_RATE buffer already produced for aubio_tempo —
 * no second decode or resample pass.
 *
 * The libkeyfinder key_t enum -> (pitch_class, mode) translation happens inside
 * the C++ bridge using libkeyfinder's own constants, so callers never need to
 * know its enum ordering.
 */
#ifndef KEYFINDER_BRIDGE_H
#define KEYFINDER_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct KeyFinderCtx KeyFinderCtx;

/* Allocates a bridge context bound to a fixed sample rate (mono input only).
 * Returns NULL on allocation failure. */
KeyFinderCtx *keyfinder_new(int sample_rate);

/* Feeds n_samples of mono float PCM (interleaving irrelevant: mono). Safe to
 * call repeatedly with successive chunks (same buffer reused each call). */
void keyfinder_feed(KeyFinderCtx *ctx, const float *samples, int n_samples);

/* Finalizes analysis and classifies the accumulated chromagram. Returns 0 and
 * writes *out_pitch_class (0-11, C=0, chromatic) and *out_mode (0=major,
 * 1=minor) on success; returns -1 (key undetermined / silence) otherwise. */
int keyfinder_result(KeyFinderCtx *ctx, int *out_pitch_class, int *out_mode);

void keyfinder_free(KeyFinderCtx *ctx);

#ifdef __cplusplus
}
#endif

#endif /* KEYFINDER_BRIDGE_H */
