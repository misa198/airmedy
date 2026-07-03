// keyfinder_bridge.cpp — C-ABI wrapper around KeyFinder::KeyFinder, libkeyfinder's
// C++ musical-key detection API. Streams mono float PCM chunk-by-chunk into
// KeyFinder::progressiveChromagram (same incremental-feed pattern Mixxx's
// AnalyzerKeyFinder uses), so the caller never needs to buffer a whole track.
//
// libkeyfinder's key_t enum pairs each of the 12 chromatic roots as
// (root_MAJOR, root_MINOR) in a fixed, library-defined order (A, Bb, B, C, Db,
// D, Eb, E, F, Gb, G, Ab). kPitchClassForRootIndex below maps that order to
// standard chromatic pitch classes (C=0) so Go never has to know libkeyfinder's
// enum layout.

#include "keyfinder_bridge.h"

#include <new>

#include <keyfinder/keyfinder.h>

struct KeyFinderCtx {
	KeyFinder::KeyFinder kf;
	KeyFinder::Workspace workspace;
	KeyFinder::AudioData audio;
	bool fed = false;
};

// Root order as declared in libkeyfinder's key_t enum (constants.h): each root
// has two consecutive enum values (MAJOR then MINOR), root index = key_t / 2.
static const int kPitchClassForRootIndex[12] = {
    9,  // A
    10, // Bb
    11, // B
    0,  // C
    1,  // Db
    2,  // D
    3,  // Eb
    4,  // E
    5,  // F
    6,  // Gb
    7,  // G
    8,  // Ab
};

extern "C" {

KeyFinderCtx *keyfinder_new(int sample_rate) {
	KeyFinderCtx *ctx = new (std::nothrow) KeyFinderCtx();
	if (!ctx) return nullptr;
	ctx->audio.setChannels(1);
	ctx->audio.setFrameRate(static_cast<unsigned int>(sample_rate));
	return ctx;
}

void keyfinder_feed(KeyFinderCtx *ctx, const float *samples, int n_samples) {
	if (!ctx || !samples || n_samples <= 0) return;

	// Mirrors Mixxx's AnalyzerKeyFinder::processSamples: AudioData's sample
	// buffer is sized once from the first chunk and every subsequent
	// same-sized chunk overwrites it in place via setSampleByFrame — callers
	// must feed constant-size chunks (true here: always FFA_TEMPO_HOP).
	if (ctx->audio.getSampleCount() == 0) {
		ctx->audio.addToSampleCount(static_cast<unsigned int>(n_samples));
	}
	for (int i = 0; i < n_samples; i++) {
		ctx->audio.setSampleByFrame(static_cast<unsigned int>(i), 0,
		                            static_cast<double>(samples[i]));
	}
	ctx->kf.progressiveChromagram(ctx->audio, ctx->workspace);
	ctx->fed = true;
}

int keyfinder_result(KeyFinderCtx *ctx, int *out_pitch_class, int *out_mode) {
	if (!ctx || !ctx->fed || !out_pitch_class || !out_mode) return -1;

	ctx->kf.finalChromagram(ctx->workspace);
	KeyFinder::key_t key = ctx->kf.keyOfChromagram(ctx->workspace);
	if (key == KeyFinder::SILENCE) return -1;

	int root_index = static_cast<int>(key) / 2;
	int mode = static_cast<int>(key) % 2; // 0 = major, 1 = minor (enum pairing)
	if (root_index < 0 || root_index >= 12) return -1;

	*out_pitch_class = kPitchClassForRootIndex[root_index];
	*out_mode = mode;
	return 0;
}

void keyfinder_free(KeyFinderCtx *ctx) { delete ctx; }

} // extern "C"
