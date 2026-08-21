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

func TestTrackRepositoryGetAllIncludesAlbumSortTitle(t *testing.T) {
	db, err := NewDB(filepath.Join(t.TempDir(), "library.db"), slog.Default())
	require.NoError(t, err)
	t.Cleanup(func() { require.NoError(t, db.Close()) })

	ctx := context.Background()
	albums := NewAlbumRepository(db)
	tracks := NewTrackRepository(db)
	artists := NewArtistRepository(db)
	genres := NewGenreRepository(db)
	composers := NewComposerRepository(db)
	require.NoError(t, albums.Save(ctx, &domain.Album{
		ID:               "album-1",
		Title:            "Displayed Album",
		SortTitle:        "Album Sort Key",
		NormalizationKey: "displayed album",
		Copyright:        "Copyright 2026",
	}))
	require.NoError(t, tracks.Save(ctx, &domain.Track{
		ID: "track-1", Path: "/music/track-1.flac", Title: "Track", SortTitle: "Track",
		AlbumID: "album-1", Format: "flac",
	}))
	require.NoError(t, artists.Save(ctx, &domain.Artist{
		ID: "album-artist-1", Name: "Album Artist", SortName: "Album Artist", NormalizationKey: "album artist",
	}))
	require.NoError(t, genres.Save(ctx, &domain.Genre{
		ID: "genre-1", Name: "Genre", NormalizationKey: "genre",
	}))
	require.NoError(t, composers.Save(ctx, &domain.Composer{
		ID: "composer-1", Name: "Composer", NormalizationKey: "composer",
	}))
	require.NoError(t, tracks.SetAlbumArtists(ctx, "track-1", []string{"album-artist-1"}))
	require.NoError(t, tracks.SetGenres(ctx, "track-1", []string{"genre-1"}))
	require.NoError(t, tracks.SetComposers(ctx, "track-1", []string{"composer-1"}))

	result, err := tracks.GetAll(ctx)
	require.NoError(t, err)
	require.Len(t, result, 1)
	require.NotNil(t, result[0].Album)
	require.Equal(t, "Album Sort Key", result[0].Album.SortTitle)
	require.Equal(t, "displayed album", result[0].Album.NormalizationKey)
	require.Equal(t, "Copyright 2026", result[0].Album.Copyright)
	require.False(t, result[0].Album.CreatedAt.IsZero())
	require.False(t, result[0].Album.UpdatedAt.IsZero())
	require.Equal(t, []string{"album-artist-1"}, artistIDs(result[0].AlbumArtists))
	require.Equal(t, []string{"genre-1"}, genreIDs(result[0].Genres))
	require.Equal(t, []string{"composer-1"}, composerIDs(result[0].Composers))
}

func artistIDs(artists []*domain.Artist) []string {
	ids := make([]string, len(artists))
	for i, artist := range artists {
		ids[i] = artist.ID
	}
	return ids
}

func genreIDs(genres []*domain.Genre) []string {
	ids := make([]string, len(genres))
	for i, genre := range genres {
		ids[i] = genre.ID
	}
	return ids
}

func composerIDs(composers []*domain.Composer) []string {
	ids := make([]string, len(composers))
	for i, composer := range composers {
		ids[i] = composer.ID
	}
	return ids
}
