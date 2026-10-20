package playlist

import (
	"airmedy/internal/domain"
	"context"
	"testing"
)

type favoritePreviewPlaylists struct{ domain.PlaylistRepository }

func (*favoritePreviewPlaylists) GetByID(context.Context, string) (*domain.Playlist, error) {
	return &domain.Playlist{ID: FavoritesPlaylistID}, nil
}

type favoritePreviewTracks struct {
	domain.TrackRepository
	where string
	args  []any
	limit int
	order string
}

func (r *favoritePreviewTracks) GetByRules(_ context.Context, where string, args []any, limit int, order string) ([]*domain.TrackDTO, error) {
	r.where, r.args, r.limit, r.order = where, args, limit, order
	return []*domain.TrackDTO{{Track: domain.Track{ID: "favorite", IsFavorite: true}}}, nil
}
func TestFavoritePreviewUsesBoundedFavoriteQuery(t *testing.T) {
	tracks := &favoritePreviewTracks{}
	s := &PlaylistService{repo: &favoritePreviewPlaylists{}, trackRepo: tracks}
	result, err := s.GetTracksPreview(context.Background(), FavoritesPlaylistID, 4)
	if err != nil {
		t.Fatal(err)
	}
	if len(result) != 1 || !result[0].IsFavorite || tracks.where != "t.is_favorite = ?" || tracks.limit != 4 || tracks.order != "t.sort_title" || len(tracks.args) != 1 || tracks.args[0] != true {
		t.Fatalf("wrong favorites preview query: %+v", tracks)
	}
}
