package domain

import "context"

type ArtworkCache interface {
	Save(ctx context.Context, data []byte, mimeType string) (string, error) // Returns the path/key to the cached artwork
	GetPath(key string) string
	GetVariantPath(key string, variant string) string
	Exists(key string) bool
	CleanupOrphaned(ctx context.Context, activeKeys map[string]bool) error
}
