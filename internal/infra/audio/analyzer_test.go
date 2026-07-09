//go:build ffmpeg_integration

// Integration tests for the in-process ffmpeg analyzer. Gated behind the
// ffmpeg_integration build tag because they require the static ffmpeg_libs to be
// built and linked (default `task verify` / CI without the libs is unaffected).
//
// Run with: go test -tags ffmpeg_integration ./internal/infra/audio/...
package audio

import (
	"context"
	"encoding/binary"
	"math"
	"math/rand"
	"os"
	"path/filepath"
	"sync"
	"testing"
)

// writeSineWAV writes a stereo 16-bit PCM WAV of a sine tone and returns its path.
func writeSineWAV(t *testing.T, freq float64, seconds float64, sampleRate int) string {
	t.Helper()
	const channels = 2
	const bitsPerSample = 16
	nSamples := int(float64(sampleRate) * seconds)
	dataLen := nSamples * channels * (bitsPerSample / 8)

	path := filepath.Join(t.TempDir(), "tone.wav")
	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("create wav: %v", err)
	}
	defer func() { _ = f.Close() }()

	byteRate := sampleRate * channels * (bitsPerSample / 8)
	blockAlign := channels * (bitsPerSample / 8)

	w := func(v any) {
		if err := binary.Write(f, binary.LittleEndian, v); err != nil {
			t.Fatalf("write wav: %v", err)
		}
	}
	f.WriteString("RIFF")
	w(uint32(36 + dataLen))
	f.WriteString("WAVE")
	f.WriteString("fmt ")
	w(uint32(16))
	w(uint16(1)) // PCM
	w(uint16(channels))
	w(uint32(sampleRate))
	w(uint32(byteRate))
	w(uint16(blockAlign))
	w(uint16(bitsPerSample))
	f.WriteString("data")
	w(uint32(dataLen))

	const amp = 0.5
	for i := 0; i < nSamples; i++ {
		s := int16(amp * math.Sin(2*math.Pi*freq*float64(i)/float64(sampleRate)) * math.MaxInt16)
		w(s) // L
		w(s) // R
	}
	return path
}

func TestAnalyzeSineTone(t *testing.T) {
	path := writeSineWAV(t, 440, 3.0, 44100)

	feat, err := NewLoudnessAnalyzer().Analyze(context.Background(), path)
	if err != nil {
		t.Fatalf("Analyze: %v", err)
	}

	if feat.AnalyzerVersion != AnalyzerVersion {
		t.Errorf("AnalyzerVersion: got %d want %d", feat.AnalyzerVersion, AnalyzerVersion)
	}
	if math.IsNaN(feat.LoudnessLUFS) || feat.LoudnessLUFS < -70 || feat.LoudnessLUFS > 0 {
		t.Errorf("LoudnessLUFS out of range: %v", feat.LoudnessLUFS)
	}
	if math.IsNaN(feat.TruePeak) || feat.TruePeak > 1.0 {
		t.Errorf("TruePeak unexpected: %v", feat.TruePeak)
	}
	for name, v := range map[string]float64{
		"centroid": feat.SpectralCentroid,
		"rolloff":  feat.SpectralRolloff,
		"flatness": feat.SpectralFlatness,
	} {
		if math.IsNaN(v) {
			t.Errorf("spectral %s is NaN", name)
		}
	}
	// A 440 Hz tone should land its spectral centroid in the low audible range.
	if feat.SpectralCentroid <= 0 {
		t.Errorf("SpectralCentroid should be positive, got %v", feat.SpectralCentroid)
	}
	t.Logf("features: LUFS=%.2f LRA=%.2f truePeak=%.2f rms=%.2f centroid=%.1f",
		feat.LoudnessLUFS, feat.LoudnessRange, feat.TruePeak, feat.RMS, feat.SpectralCentroid)
}

// writeClickWAV writes a stereo 16-bit PCM WAV with a short percussive click on
// every beat at the given BPM — a signal aubio_tempo can lock onto.
func writeClickWAV(t *testing.T, bpm float64, seconds float64, sampleRate int) string {
	t.Helper()
	const channels = 2
	nSamples := int(float64(sampleRate) * seconds)
	dataLen := nSamples * channels * 2

	path := filepath.Join(t.TempDir(), "click.wav")
	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("create wav: %v", err)
	}
	defer func() { _ = f.Close() }()

	w := func(v any) {
		if err := binary.Write(f, binary.LittleEndian, v); err != nil {
			t.Fatalf("write wav: %v", err)
		}
	}
	f.WriteString("RIFF")
	w(uint32(36 + dataLen))
	f.WriteString("WAVE")
	f.WriteString("fmt ")
	w(uint32(16))
	w(uint16(1))
	w(uint16(channels))
	w(uint32(sampleRate))
	w(uint32(sampleRate * channels * 2))
	w(uint16(channels * 2))
	w(uint16(16))
	f.WriteString("data")
	w(uint32(dataLen))

	beatPeriod := int(float64(sampleRate) * 60.0 / bpm) // samples between beats
	clickLen := sampleRate / 50                         // 20 ms click
	for i := 0; i < nSamples; i++ {
		var s int16
		pos := i % beatPeriod
		if pos < clickLen {
			// exponentially-decaying full-scale impulse
			env := math.Exp(-float64(pos) / float64(clickLen/4))
			s = int16(env * math.Sin(2*math.Pi*1000*float64(i)/float64(sampleRate)) * 32000)
		}
		w(s)
		w(s)
	}
	return path
}

func TestAnalyzeTempo(t *testing.T) {
	const bpm = 120.0
	path := writeClickWAV(t, bpm, 12.0, 44100)

	feat, err := NewLoudnessAnalyzer().Analyze(context.Background(), path)
	if err != nil {
		t.Fatalf("Analyze: %v", err)
	}
	t.Logf("detected tempo: %.2f BPM (expected ~%.0f)", feat.Tempo, bpm)

	if feat.Tempo <= 0 || math.IsNaN(feat.Tempo) {
		t.Fatalf("no tempo detected: %v", feat.Tempo)
	}
	// aubio may lock onto the half- or double-time pulse; accept those octaves.
	ok := false
	for _, mult := range []float64{1, 0.5, 2} {
		if math.Abs(feat.Tempo-bpm*mult) <= 6 {
			ok = true
			break
		}
	}
	if !ok {
		t.Errorf("tempo %.2f not within tolerance of %.0f (or its half/double)", feat.Tempo, bpm)
	}
}

// writeJitteredClickWAV is like writeClickWAV but randomizes each beat's period
// by +/-jitterFrac, producing an irregular rhythm aubio_onset can distinguish
// from the fixed-interval case.
//
// Click duration is tuned empirically: aubio's "hfc" onset peak-picker misses
// most beats of a short/sharp click train (e.g. the ~20ms clicks writeClickWAV
// uses for tempo detection) — verified via a standalone aubio-only repro
// independent of this codebase's ffmpeg/cgo wiring. A ~40ms decaying noise
// burst (clickLen below) gets picked up reliably across many seeds.
func writeJitteredClickWAV(t *testing.T, bpm float64, jitterFrac float64, seconds float64, sampleRate int) string {
	t.Helper()
	const channels = 2
	nSamples := int(float64(sampleRate) * seconds)
	dataLen := nSamples * channels * 2

	path := filepath.Join(t.TempDir(), "click_jitter.wav")
	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("create wav: %v", err)
	}
	defer func() { _ = f.Close() }()

	w := func(v any) {
		if err := binary.Write(f, binary.LittleEndian, v); err != nil {
			t.Fatalf("write wav: %v", err)
		}
	}
	f.WriteString("RIFF")
	w(uint32(36 + dataLen))
	f.WriteString("WAVE")
	f.WriteString("fmt ")
	w(uint32(16))
	w(uint16(1))
	w(uint16(channels))
	w(uint32(sampleRate))
	w(uint32(sampleRate * channels * 2))
	w(uint16(channels * 2))
	w(uint16(16))
	f.WriteString("data")
	w(uint32(dataLen))

	rng := rand.New(rand.NewSource(42))
	basePeriod := float64(sampleRate) * 60.0 / bpm
	clickLen := sampleRate * 40 / 1000 // 40 ms click

	nextBeat := 0
	beatPos := 0
	ampFactor := 1.0
	for i := 0; i < nSamples; i++ {
		var s int16
		if i == nextBeat {
			beatPos = i
			period := basePeriod * (1 + jitterFrac*(2*rng.Float64()-1))
			nextBeat = i + int(period)
			// Vary click amplitude slightly beat-to-beat (real transients never
			// repeat at identical energy): aubio's onset peak-picker adapts its
			// threshold to recent onset-detection-function values, and a train
			// of perfectly identical peaks can make it "habituate" and stop
			// firing for a stretch — an artifact of a too-clean synthetic
			// signal, not something real audio (or our variance math) hits.
			ampFactor = 0.7 + 0.3*rng.Float64()
		}
		pos := i - beatPos
		if pos >= 0 && pos < clickLen {
			// Broadband noise burst, not a narrowband tone: a pure-tone click's
			// energy is phase-sensitive to where it lands relative to hop
			// boundaries, which can cause aubio's onset peak-picker to miss
			// beats intermittently. Noise gives a clean spectral-flux impulse
			// regardless of hop alignment.
			env := math.Exp(-float64(pos) / float64(clickLen/4))
			s = int16(env * ampFactor * (rng.Float64()*2 - 1) * 32000)
		}
		w(s)
		w(s)
	}
	return path
}

func TestAnalyzeOnsetVariance(t *testing.T) {
	const bpm = 120.0
	regularPath := writeJitteredClickWAV(t, bpm, 0, 12.0, 44100)
	jitteredPath := writeJitteredClickWAV(t, bpm, 0.3, 12.0, 44100)

	regular, err := NewLoudnessAnalyzer().Analyze(context.Background(), regularPath)
	if err != nil {
		t.Fatalf("Analyze(regular): %v", err)
	}
	jittered, err := NewLoudnessAnalyzer().Analyze(context.Background(), jitteredPath)
	if err != nil {
		t.Fatalf("Analyze(jittered): %v", err)
	}

	t.Logf("onset variance: regular=%.1f jittered=%.1f", regular.OnsetVariance, jittered.OnsetVariance)

	if math.IsNaN(regular.OnsetVariance) || regular.OnsetVariance < 0 {
		t.Errorf("regular OnsetVariance out of range: %v", regular.OnsetVariance)
	}
	if math.IsNaN(jittered.OnsetVariance) || jittered.OnsetVariance < 0 {
		t.Errorf("jittered OnsetVariance out of range: %v", jittered.OnsetVariance)
	}
	if jittered.OnsetVariance <= regular.OnsetVariance {
		t.Errorf("expected jittered onset variance (%.1f) > regular onset variance (%.1f)",
			jittered.OnsetVariance, regular.OnsetVariance)
	}
}

func TestAnalyzeCancelled(t *testing.T) {
	path := writeSineWAV(t, 440, 10.0, 44100)
	ctx, cancel := context.WithCancel(context.Background())
	cancel() // cancel before starting

	if _, err := NewLoudnessAnalyzer().Analyze(ctx, path); err == nil {
		t.Fatal("expected cancellation error, got nil")
	}
}

// TestAnalyzeConcurrent guards the aubio FFT reentrancy switch (Ooura ->
// FFTW3F, see ffmpeg_analyzer.h): every aubio tempo/onset call used to be
// serialized process-wide because Ooura keeps mutable state in static/global
// work buffers, and running it concurrently corrupted the heap ("malloc: ***
// error ... pointer being freed was not allocated" -> SIGABRT under a
// multi-worker analysis pool). Run with -race to also catch any remaining
// data race, not just a hard crash.
func TestAnalyzeConcurrent(t *testing.T) {
	const bpm = 120.0
	path := writeJitteredClickWAV(t, bpm, 0.3, 6.0, 44100)

	const workers = 8
	const runsPerWorker = 4
	a := NewLoudnessAnalyzer()

	var wg sync.WaitGroup
	errs := make(chan error, workers*runsPerWorker)
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < runsPerWorker; j++ {
				if _, err := a.Analyze(context.Background(), path); err != nil {
					errs <- err
				}
			}
		}()
	}
	wg.Wait()
	close(errs)

	for err := range errs {
		t.Errorf("concurrent Analyze failed: %v", err)
	}
}
