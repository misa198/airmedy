package artwork

import (
	"context"
	"os"
	"path/filepath"
	"testing"
)

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
