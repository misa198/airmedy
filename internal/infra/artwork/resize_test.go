package artwork

import (
	"bytes"
	"image"
	"image/jpeg"
	"testing"
)

func makeTestJPEG(w, h int) []byte {
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, img, nil); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

func TestResizeToJPEG_sm(t *testing.T) {
	data := makeTestJPEG(1000, 1000)
	out, err := resizeToJPEG(data, 64, 64)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	img, _, err := image.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatalf("failed to decode output: %v", err)
	}
	b := img.Bounds()
	if b.Dx() > 64 || b.Dy() > 64 {
		t.Errorf("sm variant too large: %dx%d", b.Dx(), b.Dy())
	}
}

func TestResizeToJPEG_md(t *testing.T) {
	data := makeTestJPEG(1000, 1000)
	out, err := resizeToJPEG(data, 500, 500)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	img, _, err := image.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatalf("failed to decode output: %v", err)
	}
	b := img.Bounds()
	if b.Dx() > 500 || b.Dy() > 500 {
		t.Errorf("md variant too large: %dx%d", b.Dx(), b.Dy())
	}
}

func TestResizeToJPEG_aspectRatio(t *testing.T) {
	// 1000x500 image resized to sm (max 64x64) should be 64x32
	data := makeTestJPEG(1000, 500)
	out, err := resizeToJPEG(data, 64, 64)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	img, _, err := image.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatalf("failed to decode output: %v", err)
	}
	b := img.Bounds()
	if b.Dx() > 64 || b.Dy() > 64 {
		t.Errorf("output exceeds max bounds: %dx%d", b.Dx(), b.Dy())
	}
	if b.Dx() != 64 {
		t.Errorf("expected width 64 for wide image, got %d", b.Dx())
	}
}

func TestResizeToJPEG_smallerThanMax(t *testing.T) {
	// Image already smaller than max — should not upscale
	data := makeTestJPEG(30, 30)
	out, err := resizeToJPEG(data, 64, 64)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	img, _, err := image.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatalf("failed to decode output: %v", err)
	}
	b := img.Bounds()
	if b.Dx() != 30 || b.Dy() != 30 {
		t.Errorf("expected 30x30, got %dx%d", b.Dx(), b.Dy())
	}
}
