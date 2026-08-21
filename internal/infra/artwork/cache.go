package artwork

import (
	"bytes"
	"context"
	"crypto/sha256"
	"fmt"
	_ "golang.org/x/image/webp"
	"image"
	_ "image/jpeg"
	_ "image/png"
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

func (c *diskArtworkCache) Save(ctx context.Context, data []byte, _ string) (string, error) {
	if len(data) == 0 {
		return "", fmt.Errorf("artwork data is empty")
	}

	decoded, format, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return "", fmt.Errorf("decode artwork: %w", err)
	}
	bounds := decoded.Bounds()
	if bounds.Dx() <= 0 || bounds.Dy() <= 0 {
		return "", fmt.Errorf("artwork has invalid dimensions: %dx%d", bounds.Dx(), bounds.Dy())
	}

	hash := fmt.Sprintf("%x", sha256.Sum256(data))
	ext := ".jpg"
	switch format {
	case "png":
		ext = ".png"
	case "webp":
		ext = ".webp"
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

		// Variants are JPEGs even when their original is PNG.
		if hash, ok := variantHash(name); ok {
			if !activeKeys[hash+".jpg"] && !activeKeys[hash+".png"] {
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

// variantHash detects files like "{hash}_sm.jpg" or "{hash}_md.jpg" and
// returns the content hash and true.
func variantHash(name string) (string, bool) {
	for _, suffix := range []string{"_sm.jpg", "_md.jpg"} {
		if strings.HasSuffix(name, suffix) {
			return strings.TrimSuffix(name, suffix), true
		}
	}
	return "", false
}
