package config

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/adrg/xdg"
)

type Config struct {
	DataDir string
}

func NewConfig() (*Config, error) {
	dataDir := filepath.Join(xdg.DataHome, appDataFolder)
	if err := os.MkdirAll(dataDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create data directory: %w", err)
	}
	return &Config{DataDir: dataDir}, nil
}

func (c *Config) DBPath() string {
	return filepath.Join(c.DataDir, "airmedy.db")
}

func (c *Config) IndexPath() string {
	return filepath.Join(c.DataDir, "airmedy.bleve")
}

func (c *Config) ArtworkCachePath() string {
	return filepath.Join(c.DataDir, "artwork")
}

func (c *Config) LogDir() string {
	return filepath.Join(c.DataDir, "logs")
}

func (c *Config) LogPath() string {
	return filepath.Join(c.LogDir(), "airmedy.log")
}
