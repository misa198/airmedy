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
	"os"
	"path/filepath"
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

func TestAnalyzeCancelled(t *testing.T) {
	path := writeSineWAV(t, 440, 10.0, 44100)
	ctx, cancel := context.WithCancel(context.Background())
	cancel() // cancel before starting

	if _, err := NewLoudnessAnalyzer().Analyze(ctx, path); err == nil {
		t.Fatal("expected cancellation error, got nil")
	}
}
