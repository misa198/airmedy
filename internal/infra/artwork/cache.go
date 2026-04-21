package artwork

import (
	"context"
	"crypto/sha256"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"

	"changeme/internal/domain"
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
		return fileName, nil // already exists
	}

	if err := ioutil.WriteFile(filePath, data, 0644); err != nil {
		return "", fmt.Errorf("failed to write artwork file: %w", err)
	}

	return fileName, nil
}

func (c *diskArtworkCache) GetPath(key string) string {
	return filepath.Join(c.basePath, key)
}

func (c *diskArtworkCache) Exists(key string) bool {
	_, err := os.Stat(filepath.Join(c.basePath, key))
	return err == nil
}
