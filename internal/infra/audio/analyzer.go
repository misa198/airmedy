package audio

/*
#include <stdlib.h>
#include "ffmpeg_analyzer.h"
*/
import "C"

import (
	"context"
	"fmt"
	"time"
	"unsafe"

	"airmedy/internal/domain"
)

// AnalyzerVersion is the schema/algorithm version stamped on every analysis
// result. Bump it whenever the extracted features change so the backfill in the
// AnalysisService can re-analyze tracks with analyzed_version < this.
const AnalyzerVersion = 3

// noteNames maps a chromatic pitch class (0=C) to its standard-notation name.
var noteNames = [12]string{"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"}

// ffmpegAnalyzer implements domain.LoudnessAnalyzer using an in-process
// libavfilter graph (ebur128 + aspectralstats + astats). Each Analyze call
// builds and tears down its own decoder + filter graph, so the type is safe to
// call concurrently from a worker pool.
type ffmpegAnalyzer struct{}

// NewLoudnessAnalyzer returns the in-process ffmpeg-backed analyzer.
func NewLoudnessAnalyzer() domain.LoudnessAnalyzer {
	return &ffmpegAnalyzer{}
}

func (a *ffmpegAnalyzer) Analyze(ctx context.Context, path string) (*domain.TrackFeatures, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	cPath := C.CString(path)
	defer C.free(unsafe.Pointer(cPath))

	// Cancel flag lives in C memory so the watcher goroutine and the C decode
	// loop can share it without violating cgo's Go-pointer rules.
	cancel := (*C.int)(C.malloc(C.size_t(unsafe.Sizeof(C.int(0)))))
	*cancel = 0
	defer C.free(unsafe.Pointer(cancel))

	stop := make(chan struct{})
	defer close(stop)
	go func() {
		select {
		case <-ctx.Done():
			*cancel = 1
		case <-stop:
		}
	}()

	var res C.FFAnalysisResult
	rc := int(C.ffmpeg_analyze(cPath, &res, cancel))
	if rc != 0 {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		return nil, fmt.Errorf("analyze %s: %w", path, analyzeError(rc))
	}

	var musicalKey, mode string
	if pc := int(res.pitch_class); pc >= 0 && pc < 12 {
		if int(res.mode) == 1 {
			mode = "minor"
		} else {
			mode = "major"
		}
		musicalKey = noteNames[pc] + " " + mode
	}

	return &domain.TrackFeatures{
		AnalyzerVersion:  AnalyzerVersion,
		AnalyzedAt:       time.Now().UTC(),
		LoudnessLUFS:     float64(res.loudness_lufs),
		LoudnessRange:    float64(res.loudness_range),
		TruePeak:         float64(res.true_peak),
		RMS:              float64(res.rms),
		Crest:            float64(res.crest),
		SpectralCentroid: float64(res.spectral_centroid),
		SpectralRolloff:  float64(res.spectral_rolloff),
		SpectralFlatness: float64(res.spectral_flatness),
		SpectralFlux:     float64(res.spectral_flux),
		ZCR:              float64(res.zcr),
		Tempo:            float64(res.tempo), // BPM via aubio; 0 when no stable beat
		OnsetVariance:    float64(res.onset_variance),
		MusicalKey:       musicalKey,
		Mode:             mode,
	}, nil
}

func analyzeError(rc int) error {
	switch rc {
	case int(C.FFA_ERR_OPEN):
		return fmt.Errorf("could not open file or find an audio stream")
	case int(C.FFA_ERR_DECODER):
		return fmt.Errorf("could not open decoder")
	case int(C.FFA_ERR_GRAPH):
		return fmt.Errorf("could not build filter graph")
	case int(C.FFA_ERR_PROCESS):
		return fmt.Errorf("error while decoding/filtering")
	case int(C.FFA_ERR_CANCELLED):
		return context.Canceled
	case int(C.FFA_ERR_ALLOC):
		return fmt.Errorf("allocation failure")
	default:
		return fmt.Errorf("unknown analyzer error (%d)", rc)
	}
}
