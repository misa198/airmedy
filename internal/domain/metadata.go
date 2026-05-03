package domain

import "context"

type MetadataExtractor interface {
	Extract(ctx context.Context, path string) (*TrackDTO, error)
	ExtractArtwork(ctx context.Context, path string) ([]byte, string, error) // Returns data, mime type
}

// MetadataUpdate holds user-editable fields for writing tags back to an audio file.
type MetadataUpdate struct {
	Title       string
	Artist      string
	AlbumTitle  string
	Genre       string
	Composer    string
	Year        int
	TrackNumber int
	TotalTracks int
	DiscNumber  int
	TotalDiscs  int
	BPM         int
	Label       string
	ISRC        string
	ArtworkData []byte
	ArtworkMIME string
}

// MetadataWriter writes tag data back to audio files.
type MetadataWriter interface {
	WriteMetadata(ctx context.Context, path string, fields MetadataUpdate) error
}
