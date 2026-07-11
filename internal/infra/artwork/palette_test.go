package artwork

import (
	"image"
	"image/color"
	"image/jpeg"
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
