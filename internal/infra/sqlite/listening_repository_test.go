package sqlite

import (
	"context"
	"fmt"
	"log/slog"
	"testing"
	"time"

	"airmedy/internal/domain"
)

func TestListeningRepositoryReturnsFiftyTopArtistsAndTracks(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	repo := NewListeningRepository(db)
	now := time.Now()
	for i := 0; i < 51; i++ {
		trackID := fmt.Sprintf("track-%02d", i)
		artistID := fmt.Sprintf("artist-%02d", i)
		if err := trackRepo.Save(ctx, &domain.Track{ID: trackID, Path: "/music/" + trackID + ".flac", Title: trackID, SortTitle: trackID, Format: "flac"}); err != nil {
			t.Fatalf("save track %d: %v", i, err)
		}
		if _, err := db.ExecContext(ctx, `INSERT INTO artists (id, name, sort_name) VALUES (?, ?, ?)`, artistID, artistID, artistID); err != nil {
			t.Fatalf("save artist %d: %v", i, err)
		}
		if _, err := db.ExecContext(ctx, `INSERT INTO track_artists (track_id, artist_id, position) VALUES (?, ?, 0)`, trackID, artistID); err != nil {
			t.Fatalf("link artist %d: %v", i, err)
		}
		if err := repo.RecordSession(ctx, domain.ListeningSession{TrackID: trackID, StartedAt: now, EndedAt: now.Add(time.Minute), ListenedSeconds: 60, QualifiedPlay: true}); err != nil {
			t.Fatalf("record session %d: %v", i, err)
		}
	}

	insights, err := repo.GetInsights(ctx, domain.ListeningRangeAll, now)
	if err != nil {
		t.Fatalf("get insights: %v", err)
	}
	if len(insights.TopArtists) != 50 || len(insights.TopTracks) != 50 {
		t.Fatalf("top insight counts = artists:%d tracks:%d, want 50 each", len(insights.TopArtists), len(insights.TopTracks))
	}
}

func TestListeningRepositoryRanksTopTracksByPlayCountThenListeningTime(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	for _, track := range []*domain.Track{
		{ID: "alphabetical", Path: "/music/alphabetical.flac", Title: "A Track", SortTitle: "A Track", Format: "flac"},
		{ID: "longer", Path: "/music/longer.flac", Title: "Z Track", SortTitle: "Z Track", Format: "flac"},
	} {
		if err := trackRepo.Save(ctx, track); err != nil {
			t.Fatalf("save track %q: %v", track.ID, err)
		}
	}

	repo := NewListeningRepository(db)
	now := time.Now()
	for _, session := range []domain.ListeningSession{
		{TrackID: "alphabetical", StartedAt: now.Add(-2 * time.Minute), EndedAt: now.Add(-time.Minute), ListenedSeconds: 60, QualifiedPlay: true},
		{TrackID: "longer", StartedAt: now.Add(-3 * time.Minute), EndedAt: now.Add(-time.Minute), ListenedSeconds: 120, QualifiedPlay: true},
	} {
		if err := repo.RecordSession(ctx, session); err != nil {
			t.Fatalf("record session for %q: %v", session.TrackID, err)
		}
	}

	insights, err := repo.GetInsights(ctx, domain.ListeningRangeAll, now)
	if err != nil {
		t.Fatalf("get insights: %v", err)
	}
	if len(insights.TopTracks) != 2 || insights.TopTracks[0].ID != "longer" || insights.TopTracks[1].ID != "alphabetical" {
		t.Fatalf("top track order = %#v, want longer-listened track before alphabetical title", insights.TopTracks)
	}
}

func TestListeningRepositoryRollsUpSessions(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()
	ctx := context.Background()
	if err := NewTrackRepository(db).Save(ctx, &domain.Track{ID: "track-1", Path: "/music/one.flac", Title: "One", SortTitle: "One", Format: "flac", FileSize: 1024}); err != nil {
		t.Fatalf("save track: %v", err)
	}
	if _, err := db.Exec(`INSERT INTO artists (id, name, sort_name) VALUES ('artist-1', 'Artist One', 'Artist One'), ('artist-2', 'Artist Two', 'Artist Two'); INSERT INTO track_artists (track_id, artist_id, position) VALUES ('track-1', 'artist-1', 0), ('track-1', 'artist-2', 1)`); err != nil {
		t.Fatalf("seed artists: %v", err)
	}
	if _, err := db.Exec(`INSERT INTO albums (id, title, sort_title) VALUES ('album-1', 'Album One', 'Album One'); INSERT INTO playlists (id, name) VALUES ('playlist-1', 'Playlist One'), ('playlist-2', 'Playlist Two'), ('favorites', 'Favorites')`); err != nil {
		t.Fatalf("seed library summary: %v", err)
	}
	if _, err := db.Exec(`INSERT INTO genres (id, name) VALUES ('genre-1', 'Genre 1'), ('genre-2', 'Genre 2'), ('genre-3', 'Genre 3'), ('genre-4', 'Genre 4'), ('genre-5', 'Genre 5'), ('genre-6', 'Genre 6'); INSERT INTO track_genres (track_id, genre_id, position) VALUES ('track-1', 'genre-1', 0), ('track-1', 'genre-2', 1), ('track-1', 'genre-3', 2), ('track-1', 'genre-4', 3), ('track-1', 'genre-5', 4), ('track-1', 'genre-6', 5)`); err != nil {
		t.Fatalf("seed genres: %v", err)
	}
	repo := NewListeningRepository(db)
	now := time.Now()
	for _, session := range []domain.ListeningSession{
		{TrackID: "track-1", StartedAt: now.Add(-5 * time.Minute), EndedAt: now.Add(-4 * time.Minute), ListenedSeconds: 60, QualifiedPlay: true},
		{TrackID: "track-1", StartedAt: now.Add(-3 * time.Minute), EndedAt: now.Add(-2 * time.Minute), ListenedSeconds: 45, QualifiedPlay: false},
	} {
		if err := repo.RecordSession(ctx, session); err != nil {
			t.Fatalf("record session: %v", err)
		}
	}
	insights, err := repo.GetInsights(ctx, domain.ListeningRange7D, now)
	if err != nil {
		t.Fatalf("get insights: %v", err)
	}
	if insights.ListenedSeconds != 105 || insights.Plays != 1 {
		t.Fatalf("unexpected aggregate: %#v", insights)
	}
	if insights.LibraryTracks != 1 || insights.LibraryAlbums != 1 || insights.LibraryArtists != 2 || insights.LibraryPlaylists != 2 || insights.LibraryBytes != 1024 {
		t.Fatalf("unexpected library summary: %#v", insights)
	}
	if len(insights.TopTracks) != 1 || insights.TopTracks[0].PlayCount != 1 || insights.TopTracks[0].ListenedSeconds != 105 {
		t.Fatalf("unexpected top tracks: %#v", insights.TopTracks)
	}
	if len(insights.Activity) != 7 {
		t.Fatalf("expected zero-filled 7-day activity, got %d points", len(insights.Activity))
	}
	if insights.TopTracks[0].Artist != "Artist One, Artist Two" {
		t.Fatalf("unexpected artist label: %q", insights.TopTracks[0].Artist)
	}
	if len(insights.Genres) != 6 || insights.Genres[0].Name != "Genre 1" || !insights.Genres[5].IsOther || insights.Genres[5].ListenedSeconds != 105 {
		t.Fatalf("unexpected listening genres: %#v", insights.Genres)
	}
}

func TestListeningRepositoryCleanupAndQualityClassification(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()
	ctx := context.Background()
	tracks := []*domain.Track{
		{ID: "hires", Path: "/hires.flac", Title: "Hi", SortTitle: "Hi", Format: "flac", BitDepth: 24},
		{ID: "legacy", Path: "/legacy.m4a", Title: "Legacy", SortTitle: "Legacy", Format: "m4a"},
	}
	trackRepo := NewTrackRepository(db)
	for _, track := range tracks {
		if err := trackRepo.Save(ctx, track); err != nil {
			t.Fatalf("save %s: %v", track.ID, err)
		}
	}
	repo := NewListeningRepository(db)
	old := time.Now().AddDate(0, 0, -181)
	if err := repo.RecordSession(ctx, domain.ListeningSession{TrackID: "hires", StartedAt: old, EndedAt: old.Add(time.Minute), ListenedSeconds: 60}); err != nil {
		t.Fatalf("record old session: %v", err)
	}
	if err := repo.CleanupSessions(ctx, time.Now().AddDate(0, 0, -180)); err != nil {
		t.Fatalf("cleanup: %v", err)
	}
	var rawCount int
	if err := db.Get(&rawCount, `SELECT COUNT(*) FROM listening_sessions`); err != nil || rawCount != 0 {
		t.Fatalf("raw sessions not cleaned: count=%d err=%v", rawCount, err)
	}
	insights, err := repo.GetInsights(ctx, domain.ListeningRangeAll, time.Now())
	if err != nil {
		t.Fatalf("get insights: %v", err)
	}
	kinds := map[string]int{}
	for _, bucket := range insights.Quality {
		kinds[bucket.Kind] = bucket.Count
	}
	if kinds["hi_res"] != 1 || kinds["unknown"] != 1 {
		t.Fatalf("unexpected quality buckets: %#v", kinds)
	}
}

func TestSplitListeningByLocalDate(t *testing.T) {
	location := time.Local
	start := time.Date(2026, 7, 22, 23, 59, 50, 0, location)
	end := time.Date(2026, 7, 23, 0, 0, 10, 0, location)
	parts := splitListeningByLocalDate(start, end, 20)
	if parts["2026-07-22"] != 10 || parts["2026-07-23"] != 10 {
		t.Fatalf("unexpected midnight split: %#v", parts)
	}
}
