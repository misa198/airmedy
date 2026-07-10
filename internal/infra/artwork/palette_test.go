package artwork

import (
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func writeSolidJPEG(t *testing.T, dir string, c color.RGBA) string {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, 100, 100))
	for y := range 100 {
		for x := range 100 {
			img.SetRGBA(x, y, c)
		}
	}
	path := filepath.Join(dir, "test.jpg")
	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("create test image: %v", err)
	}
	defer func() { _ = f.Close() }()
	if err := jpeg.Encode(f, img, nil); err != nil {
		t.Fatalf("encode test image: %v", err)
	}
	return path
}

func TestExtractPalette_SolidColor(t *testing.T) {
	dir := t.TempDir()
	red := color.RGBA{R: 220, G: 10, B: 30, A: 255}
	path := writeSolidJPEG(t, dir, red)

	palette, err := ExtractPalette(path)
	if err != nil {
		t.Fatalf("ExtractPalette: %v", err)
	}

	// All three clusters should converge to approximately the same red color.
	for _, hex := range []string{palette.Vibrant, palette.Muted, palette.Dominant} {
		if !strings.HasPrefix(hex, "#") || len(hex) != 7 {
			t.Errorf("invalid hex format: %q", hex)
		}
	}
}

// TestExtractPalette_MicroLogoSuppressed verifies that a tiny hyper-saturated
// region (simulating a Netflix-style logo in the corner) does NOT hijack the
// vibrant slot when the rest of the cover is muted/white.
//
// A 40×40 red patch on a 1000×1000 image covers 0.16% of pixels. After
// downsampling to 256×256 it covers ~0.1% — well below accentMinAreaFraction
// (0.4%), so it is outright rejected and the vibrant falls back to k-means.
func TestExtractPalette_MicroLogoSuppressed(t *testing.T) {
	dir := t.TempDir()
	img := image.NewRGBA(image.Rect(0, 0, 1000, 1000))
	white := color.RGBA{R: 250, G: 250, B: 250, A: 255}
	red := color.RGBA{R: 220, G: 20, B: 35, A: 255}
	for y := range 1000 {
		for x := range 1000 {
			img.SetRGBA(x, y, white)
		}
	}
	// 40×40 = 1,600 px out of 1,000,000 (0.16%) — a classic micro-logo.
	for y := 900; y < 940; y++ {
		for x := 900; x < 940; x++ {
			img.SetRGBA(x, y, red)
		}
	}

	path := filepath.Join(dir, "micro_logo.png")
	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("create image: %v", err)
	}
	if err := png.Encode(f, img); err != nil {
		_ = f.Close()
		t.Fatalf("encode image: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("close image: %v", err)
	}

	palette, err := ExtractPalette(path)
	if err != nil {
		t.Fatalf("ExtractPalette: %v", err)
	}
	// The micro-logo must NOT be the vibrant result. The accent pass should
	// reject it (below minAreaFraction) and k-means falls back to the dominant
	// white, so Vibrant must NOT be red.
	if palette.Vibrant == "#DC1423" {
		t.Errorf("Vibrant = %s — micro-logo red must not win; want fallback (white/grey)", palette.Vibrant)
	}
}

// TestExtractPalette_LargeAccentWins verifies that a deliberate accent region
// covering ≥2% of the image still beats the muted background. A 130×130 block
// on a 1000×1000 image is ~1.7% of pixels; after downsampling to 256×256 it
// maps to ~33×33 ≈ 1,089 px out of 65,536 (~1.7%), which clears accentMinAreaFraction
// and has areaWeight ≈ 0.92 — enough for a vibrant red to win.
func TestExtractPalette_LargeAccentWins(t *testing.T) {
	dir := t.TempDir()
	img := image.NewRGBA(image.Rect(0, 0, 1000, 1000))
	beige := color.RGBA{R: 210, G: 200, B: 190, A: 255}
	red := color.RGBA{R: 220, G: 20, B: 35, A: 255}
	for y := range 1000 {
		for x := range 1000 {
			img.SetRGBA(x, y, beige)
		}
	}
	// 130×130 = 16,900 px (~1.69%) — a large colorful title block.
	for y := 400; y < 530; y++ {
		for x := 400; x < 530; x++ {
			img.SetRGBA(x, y, red)
		}
	}

	path := filepath.Join(dir, "large_accent.png")
	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("create image: %v", err)
	}
	if err := png.Encode(f, img); err != nil {
		_ = f.Close()
		t.Fatalf("encode image: %v", err)
	}
	if err := f.Close(); err != nil {
		t.Fatalf("close image: %v", err)
	}

	palette, err := ExtractPalette(path)
	if err != nil {
		t.Fatalf("ExtractPalette: %v", err)
	}
	// The large red block should still be caught by the accent pass.
	if palette.Vibrant != "#DC1423" {
		t.Errorf("Vibrant = %s, want large red accent #DC1423", palette.Vibrant)
	}
}

func TestExtractPalette_MissingFile(t *testing.T) {
	_, err := ExtractPalette("/nonexistent/path/image.jpg")
	if err == nil {
		t.Error("expected error for missing file, got nil")
	}
}

func TestToHex(t *testing.T) {
	tests := []struct {
		c    color.RGBA
		want string
	}{
		{color.RGBA{R: 255, G: 0, B: 0, A: 255}, "#FF0000"},
		{color.RGBA{R: 0, G: 255, B: 0, A: 255}, "#00FF00"},
		{color.RGBA{R: 0, G: 0, B: 255, A: 255}, "#0000FF"},
		{color.RGBA{R: 0, G: 0, B: 0, A: 255}, "#000000"},
	}
	for _, tt := range tests {
		if got := toHex(tt.c); got != tt.want {
			t.Errorf("toHex(%v) = %q, want %q", tt.c, got, tt.want)
		}
	}
}
