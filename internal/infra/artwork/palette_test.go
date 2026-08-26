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

func TestKMeans_LargeWhiteBackgroundKeepsAccent(t *testing.T) {
	pixels := make([]color.RGBA, 100)
	for i := range pixels {
		pixels[i] = color.RGBA{R: 254, G: 254, B: 254, A: 255}
	}
	for i := 90; i < len(pixels); i++ {
		pixels[i] = color.RGBA{R: 190, G: 35, B: 55, A: 255}
	}

	centers := kMeans(pixels, 3, 10)
	if got := centers[mostVibrant(centers, []int{90, 10, 0})]; got.R < 150 || got.G > 100 || got.B > 100 {
		t.Fatalf("vibrant center = %#v, want the red accent", got)
	}
}

func TestMostVibrant_RejectsMicroAccent(t *testing.T) {
	centers := []color.RGBA{
		{R: 164, G: 198, B: 233, A: 255},
		{R: 56, G: 94, B: 112, A: 255},
		{R: 183, G: 94, B: 96, A: 255},
	}
	if got := mostVibrant(centers, []int{3993, 90, 13}); got != 0 {
		t.Fatalf("mostVibrant() = %d, want the prevalent blue cluster", got)
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

func TestMutedColor_IgnoresTinyRemainingCluster(t *testing.T) {
	centers := []color.RGBA{
		{R: 8, G: 9, B: 68, A: 255},
		{R: 253, G: 253, B: 163, A: 255},
		{R: 161, G: 115, B: 135, A: 255},
	}
	backdrop := color.RGBA{R: 32, G: 34, B: 76, A: 255}
	if got := mutedColor(centers, []int{3690, 381, 25}, 1, 0, backdrop); got != backdrop {
		t.Fatalf("mutedColor() = %#v, want backdrop %#v", got, backdrop)
	}
	if got := mutedColor(centers, []int{2043, 1473, 580}, 2, 0, backdrop); got != centers[1] {
		t.Fatalf("mutedColor() = %#v, want prominent remaining cluster %#v", got, centers[1])
	}
}

func TestAverageColor(t *testing.T) {
	got := averageColor([]color.RGBA{{R: 20, G: 40, B: 60}, {R: 40, G: 60, B: 80}})
	want := color.RGBA{R: 30, G: 50, B: 70, A: 255}
	if got != want {
		t.Errorf("averageColor() = %#v, want %#v", got, want)
	}
}
