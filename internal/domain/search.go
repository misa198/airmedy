package domain

import "context"

type SearchResult struct {
	ID    string  `json:"id"`
	Type  string  `json:"type"` // track, album, artist
	Score float64 `json:"score"`
}

type SearchService interface {
	IndexTrack(ctx context.Context, track *TrackDTO) error
	IndexAlbum(ctx context.Context, album *AlbumDTO) error
	IndexArtist(ctx context.Context, artist *Artist) error
	Search(ctx context.Context, query string) ([]SearchResult, error)
	DeleteFromIndex(ctx context.Context, id string) error
	Close() error
}
