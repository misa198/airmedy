package domain

import "context"

type MetadataExtractor interface {
	Extract(ctx context.Context, path string) (*TrackDTO, error)
	ExtractArtwork(ctx context.Context, path string) ([]byte, string, error) // Returns data, mime type
}
