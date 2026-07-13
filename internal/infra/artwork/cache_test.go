package artwork

import (
	"bytes"
	"context"
	"image"
	"image/png"
	"os"
	"path/filepath"
	"testing"
)

func makeTestPNG(w, h int) []byte {
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		panic(err)
	}
	return buf.Bytes()
}

func TestSave_CreatesVariants(t *testing.T) {
	dir := t.TempDir()
	cache, err := NewDiskArtworkCache(dir)
	if err != nil {
		t.Fatalf("NewDiskArtworkCache: %v", err)
	}

	data := makeTestJPEG(200, 200)
	key, err := cache.Save(context.Background(), data, "image/jpeg")
	if err != nil {
		t.Fatalf("Save: %v", err)
	}

	ext := filepath.Ext(key)
	base := key[:len(key)-len(ext)]

	for _, variant := range []string{"_sm", "_md"} {
		path := filepath.Join(dir, base+variant+".jpg")
		if _, err := os.Stat(path); os.IsNotExist(err) {
			t.Errorf("variant %s not created", variant)
		}
	}
}

func TestGetVariantPath(t *testing.T) {
	dir := t.TempDir()
	cache, err := NewDiskArtworkCache(dir)
	if err != nil {
		t.Fatalf("NewDiskArtworkCache: %v", err)
	}

	key := "abc123.jpg"
	smPath := cache.GetVariantPath(key, "sm")
	mdPath := cache.GetVariantPath(key, "md")

	expectedSm := filepath.Join(dir, "abc123_sm.jpg")
	expectedMd := filepath.Join(dir, "abc123_md.jpg")

	if smPath != expectedSm {
		t.Errorf("sm path: got %s, want %s", smPath, expectedSm)
	}
	if mdPath != expectedMd {
		t.Errorf("md path: got %s, want %s", mdPath, expectedMd)
	}
}

func TestSave_RejectsInvalidArtwork(t *testing.T) {
	cache, err := NewDiskArtworkCache(t.TempDir())
	if err != nil {
		t.Fatalf("NewDiskArtworkCache: %v", err)
	}

	for _, data := range [][]byte{nil, []byte("not an image")} {
		if _, err := cache.Save(context.Background(), data, "image/jpeg"); err == nil {
			t.Errorf("Save(%q) succeeded for invalid artwork", data)
		}
	}
}

func TestSave_UsesDecodedFormatInsteadOfMIME(t *testing.T) {
	cache, err := NewDiskArtworkCache(t.TempDir())
	if err != nil {
		t.Fatalf("NewDiskArtworkCache: %v", err)
	}

	key, err := cache.Save(context.Background(), makeTestPNG(32, 32), "image/jpeg")
	if err != nil {
		t.Fatalf("Save: %v", err)
	}
	if filepath.Ext(key) != ".png" {
		t.Errorf("key extension = %q, want .png", filepath.Ext(key))
	}
}

func TestCleanupOrphaned_RemovesVariants(t *testing.T) {
	dir := t.TempDir()
	cache, err := NewDiskArtworkCache(dir)
	if err != nil {
		t.Fatalf("NewDiskArtworkCache: %v", err)
	}

	data := makeTestJPEG(200, 200)
	key, err := cache.Save(context.Background(), data, "image/jpeg")
	if err != nil {
		t.Fatalf("Save: %v", err)
	}

	// Cleanup with empty active keys — all files should be removed
	if err := cache.CleanupOrphaned(context.Background(), map[string]bool{}); err != nil {
		t.Fatalf("CleanupOrphaned: %v", err)
	}

	ext := filepath.Ext(key)
	base := key[:len(key)-len(ext)]
	for _, name := range []string{key, base + "_sm.jpg", base + "_md.jpg"} {
		if _, err := os.Stat(filepath.Join(dir, name)); !os.IsNotExist(err) {
			t.Errorf("expected %s to be removed", name)
		}
	}
}

func TestCleanupOrphaned_KeepsActiveVariants(t *testing.T) {
	dir := t.TempDir()
	cache, err := NewDiskArtworkCache(dir)
	if err != nil {
		t.Fatalf("NewDiskArtworkCache: %v", err)
	}

	data := makeTestJPEG(200, 200)
	key, err := cache.Save(context.Background(), data, "image/jpeg")
	if err != nil {
		t.Fatalf("Save: %v", err)
	}

	// Cleanup with this key active — nothing should be removed
	if err := cache.CleanupOrphaned(context.Background(), map[string]bool{key: true}); err != nil {
		t.Fatalf("CleanupOrphaned: %v", err)
	}

	ext := filepath.Ext(key)
	base := key[:len(key)-len(ext)]
	for _, name := range []string{key, base + "_sm.jpg", base + "_md.jpg"} {
		if _, err := os.Stat(filepath.Join(dir, name)); os.IsNotExist(err) {
			t.Errorf("expected %s to be kept", name)
		}
	}
}

func TestCleanupOrphaned_KeepsPNGVariants(t *testing.T) {
	dir := t.TempDir()
	cache, err := NewDiskArtworkCache(dir)
	if err != nil {
		t.Fatalf("NewDiskArtworkCache: %v", err)
	}

	key, err := cache.Save(context.Background(), makeTestPNG(200, 200), "image/png")
	if err != nil {
		t.Fatalf("Save: %v", err)
	}
	if err := cache.CleanupOrphaned(context.Background(), map[string]bool{key: true}); err != nil {
		t.Fatalf("CleanupOrphaned: %v", err)
	}

	base := key[:len(key)-len(filepath.Ext(key))]
	for _, name := range []string{base + "_sm.jpg", base + "_md.jpg"} {
		if _, err := os.Stat(filepath.Join(dir, name)); err != nil {
			t.Errorf("expected %s to be kept: %v", name, err)
		}
	}
}
