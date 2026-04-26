package artwork

import (
	"context"
	"crypto/sha256"
	"fmt"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"sync"

	"airmedy/internal/domain"
)

type diskArtworkCache struct {
	basePath string
}

func NewDiskArtworkCache(basePath string) (domain.ArtworkCache, error) {
	if err := os.MkdirAll(basePath, 0755); err != nil {
		return nil, fmt.Errorf("failed to create artwork cache directory: %w", err)
	}
	return &diskArtworkCache{basePath: basePath}, nil
}

func (c *diskArtworkCache) Save(ctx context.Context, data []byte, mimeType string) (string, error) {
	hash := fmt.Sprintf("%x", sha256.Sum256(data))
	ext := ".jpg"
	if mimeType == "image/png" {
		ext = ".png"
	}

	fileName := hash + ext
	filePath := filepath.Join(c.basePath, fileName)

	if _, err := os.Stat(filePath); err == nil {
		c.saveVariants(data, hash)
		return fileName, nil
	}

	if err := os.WriteFile(filePath, data, 0644); err != nil {
		return "", fmt.Errorf("failed to write artwork file: %w", err)
	}

	c.saveVariants(data, hash)

	return fileName, nil
}

// saveVariants creates _sm and _md JPEG variants if they don't already exist.
func (c *diskArtworkCache) saveVariants(data []byte, hash string) {
	type variant struct {
		suffix string
		maxW   int
		maxH   int
	}
	variants := []variant{
		{"_sm", 64, 64},
		{"_md", 500, 500},
	}
	var wg sync.WaitGroup
	for _, v := range variants {
		path := filepath.Join(c.basePath, hash+v.suffix+".jpg")
		if _, err := os.Stat(path); err == nil {
			continue
		}
		wg.Add(1)
		go func(v variant, path string) {
			defer wg.Done()
			resized, err := resizeToJPEG(data, v.maxW, v.maxH)
			if err != nil {
				slog.Warn("Failed to generate artwork variant", "suffix", v.suffix, "error", err)
				return
			}
			if err := os.WriteFile(path, resized, 0644); err != nil {
				slog.Warn("Failed to write artwork variant", "suffix", v.suffix, "error", err)
			}
		}(v, path)
	}
	wg.Wait()
}

func (c *diskArtworkCache) GetPath(key string) string {
	return filepath.Join(c.basePath, key)
}

func (c *diskArtworkCache) GetVariantPath(key, variant string) string {
	ext := filepath.Ext(key)
	base := strings.TrimSuffix(key, ext)
	return filepath.Join(c.basePath, base+"_"+variant+".jpg")
}

func (c *diskArtworkCache) Exists(key string) bool {
	_, err := os.Stat(filepath.Join(c.basePath, key))
	return err == nil
}

func (c *diskArtworkCache) CleanupOrphaned(ctx context.Context, activeKeys map[string]bool) error {
	entries, err := os.ReadDir(c.basePath)
	if err != nil {
		return fmt.Errorf("failed to read artwork cache directory: %w", err)
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}

		name := entry.Name()

		// Variant files: keep if their base key is active.
		if baseKey, ok := variantBaseKey(name); ok {
			if !activeKeys[baseKey] {
				os.Remove(filepath.Join(c.basePath, name)) //nolint:errcheck
			}
			continue
		}

		if !activeKeys[name] {
			os.Remove(filepath.Join(c.basePath, name)) //nolint:errcheck
		}
	}

	return nil
}

// variantBaseKey detects files like "{hash}_sm.jpg" or "{hash}_md.jpg" and
// returns the base key ("{hash}.jpg") and true. Returns "", false otherwise.
func variantBaseKey(name string) (string, bool) {
	for _, suffix := range []string{"_sm.jpg", "_md.jpg"} {
		if strings.HasSuffix(name, suffix) {
			hash := strings.TrimSuffix(name, suffix)
			return hash + ".jpg", true
		}
	}
	return "", false
}
