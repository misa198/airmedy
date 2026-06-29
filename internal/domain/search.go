package domain

import "context"

type SearchResult struct {
	ID    string  `json:"id"`
	Type  string  `json:"type"` // track, album, artist
	Score float64 `json:"score"`
}

// ReindexData holds every entity to be re-indexed in one batch pass.
type ReindexData struct {
	Tracks    []*TrackDTO
	Albums    []*AlbumDTO
	Artists   []*Artist
	Composers []*Composer
	Playlists []*Playlist
}

type SearchService interface {
	IndexTrack(ctx context.Context, track *TrackDTO) error
	IndexAlbum(ctx context.Context, album *AlbumDTO) error
	IndexArtist(ctx context.Context, artist *Artist) error
	IndexPlaylist(ctx context.Context, playlist *Playlist) error
	IndexComposer(ctx context.Context, composer *Composer) error
	// BatchReindex writes all of data into the index using batched commits
	// (one fsync per chunk, not per document). onProgress, if non-nil, is
	// called once per item with a human-readable label.
	BatchReindex(ctx context.Context, data *ReindexData, onProgress func(path string)) error
	Search(ctx context.Context, query string) ([]SearchResult, error)
	DeleteFromIndex(ctx context.Context, id string) error
	// DeleteAlbumFromIndex removes an album document (keyed by the "album:" prefix)
	// from the search index.
	DeleteAlbumFromIndex(ctx context.Context, albumID string) error
	// DeleteArtistFromIndex removes an artist document (keyed by the "artist:" prefix).
	DeleteArtistFromIndex(ctx context.Context, artistID string) error
	// DeleteComposerFromIndex removes a composer document (keyed by the "composer:" prefix).
	DeleteComposerFromIndex(ctx context.Context, composerID string) error
	DocCount(ctx context.Context) (uint64, error)
	Reset(ctx context.Context) error
	Close() error
}
