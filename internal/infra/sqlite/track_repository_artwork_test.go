package sqlite

import (
	"context"
	"log/slog"
	"path/filepath"
	"testing"

	"airmedy/internal/domain"
	"github.com/stretchr/testify/require"
)

func TestTrackRepositoryGetAllIncludesArtistArtworkSources(t *testing.T) {
	db, err := NewDB(filepath.Join(t.TempDir(), "library.db"), slog.Default())
	require.NoError(t, err)
	t.Cleanup(func() { require.NoError(t, db.Close()) })

	ctx := context.Background()
	tracks := NewTrackRepository(db)
	artists := NewArtistRepository(db)
	require.NoError(t, tracks.Save(ctx, &domain.Track{
		ID: "track-1", Path: "/music/track-1.flac", Title: "Track", SortTitle: "Track", Format: "flac",
	}))
	require.NoError(t, artists.Save(ctx, &domain.Artist{
		ID: "artist-1", Name: "Artist", SortName: "Artist", NormalizationKey: "artist",
	}))
	artworkKey := "artist-artwork"
	require.NoError(t, artists.SetArtworkSource(ctx, "artist-1", domain.ArtworkSourceLocalFile, &artworkKey))
	require.NoError(t, tracks.SetArtists(ctx, "track-1", []string{"artist-1"}))

	result, err := tracks.GetAll(ctx)
	require.NoError(t, err)
	require.Len(t, result, 1)
	require.Len(t, result[0].Artists, 1)
	require.NotNil(t, result[0].Artists[0].ArtworkKeyLocal)
	require.Equal(t, artworkKey, *result[0].Artists[0].ArtworkKeyLocal)
}
