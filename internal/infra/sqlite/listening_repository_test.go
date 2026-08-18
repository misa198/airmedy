package sqlite

import (
	"context"
	"fmt"
	"log/slog"
	"reflect"
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

func TestListeningRepositoryReturnsCumulativeLibraryGrowth(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	now := time.Date(2026, time.July, 24, 15, 0, 0, 0, time.Local)
	for _, track := range []*domain.Track{
		{ID: "old", Path: "/music/old.flac", Title: "Old", SortTitle: "Old", Format: "flac", CreatedAt: time.Date(2026, time.July, 15, 12, 0, 0, 0, time.Local)},
		{ID: "day-19", Path: "/music/day-19.flac", Title: "Day 19", SortTitle: "Day 19", Format: "flac", CreatedAt: time.Date(2026, time.July, 19, 12, 0, 0, 0, time.Local)},
		{ID: "day-21-a", Path: "/music/day-21-a.flac", Title: "Day 21 A", SortTitle: "Day 21 A", Format: "flac", CreatedAt: time.Date(2026, time.July, 21, 12, 0, 0, 0, time.Local)},
		{ID: "day-21-b", Path: "/music/day-21-b.flac", Title: "Day 21 B", SortTitle: "Day 21 B", Format: "flac", CreatedAt: time.Date(2026, time.July, 21, 13, 0, 0, 0, time.Local)},
	} {
		if err := trackRepo.Save(ctx, track); err != nil {
			t.Fatalf("save track %q: %v", track.ID, err)
		}
	}

	repo := NewListeningRepository(db)
	insights, err := repo.GetInsights(ctx, domain.ListeningRange7D, now)
	if err != nil {
		t.Fatalf("get 7d insights: %v", err)
	}
	if got, want := insights.LibraryGrowth, []domain.AnalyticsLibraryGrowthPoint{
		{Date: "2026-07-18", TrackCount: 1}, {Date: "2026-07-19", TrackCount: 2},
		{Date: "2026-07-20", TrackCount: 2}, {Date: "2026-07-21", TrackCount: 4},
		{Date: "2026-07-22", TrackCount: 4}, {Date: "2026-07-23", TrackCount: 4},
		{Date: "2026-07-24", TrackCount: 4},
	}; !reflect.DeepEqual(got, want) {
		t.Fatalf("7d library growth = %#v, want %#v", got, want)
	}

	insights, err = repo.GetInsights(ctx, domain.ListeningRange30D, now)
	if err != nil {
		t.Fatalf("get 30d insights: %v", err)
	}
	if len(insights.LibraryGrowth) != 30 || insights.LibraryGrowth[0].Date != "2026-06-25" || insights.LibraryGrowth[len(insights.LibraryGrowth)-1].TrackCount != 4 {
		t.Fatalf("unexpected 30d library growth: %#v", insights.LibraryGrowth)
	}

	if err := trackRepo.Save(ctx, &domain.Track{ID: "year-2024", Path: "/music/year-2024.flac", Title: "2024", SortTitle: "2024", Format: "flac", CreatedAt: time.Date(2024, time.June, 1, 12, 0, 0, 0, time.Local)}); err != nil {
		t.Fatalf("save 2024 track: %v", err)
	}
	if err := trackRepo.Save(ctx, &domain.Track{ID: "year-2025", Path: "/music/year-2025.flac", Title: "2025", SortTitle: "2025", Format: "flac", CreatedAt: time.Date(2025, time.June, 1, 12, 0, 0, 0, time.Local)}); err != nil {
		t.Fatalf("save 2025 track: %v", err)
	}
	insights, err = repo.GetInsights(ctx, domain.ListeningRangeAll, now)
	if err != nil {
		t.Fatalf("get all insights: %v", err)
	}
	if got, want := insights.LibraryGrowth, []domain.AnalyticsLibraryGrowthPoint{
		{Date: "2024", TrackCount: 1}, {Date: "2025", TrackCount: 2}, {Date: "2026", TrackCount: 6},
	}; !reflect.DeepEqual(got, want) {
		t.Fatalf("all library growth = %#v, want %#v", got, want)
	}
}

func TestListeningRepositoryReturnsCurrentListeningStreak(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	if err := NewTrackRepository(db).Save(ctx, &domain.Track{ID: "streak-track", Path: "/music/streak.flac", Title: "Streak", SortTitle: "Streak", Format: "flac"}); err != nil {
		t.Fatalf("save track: %v", err)
	}
	repo := NewListeningRepository(db)
	now := time.Date(2026, time.July, 24, 15, 0, 0, 0, time.Local)
	seedStreak := func(offsets ...int) {
		t.Helper()
		if _, err := db.ExecContext(ctx, `DELETE FROM daily_track_listening_stats`); err != nil {
			t.Fatalf("clear listening stats: %v", err)
		}
		for _, offset := range offsets {
			if _, err := db.ExecContext(ctx, `INSERT INTO daily_track_listening_stats (local_date, track_id, listened_seconds) VALUES (?, 'streak-track', 60)`, now.AddDate(0, 0, offset).Format("2006-01-02")); err != nil {
				t.Fatalf("seed listening stat for offset %d: %v", offset, err)
			}
		}
	}

	seedStreak(0, -1, -2, -3, -4, -5, -6, -7, -8, -9)
	for _, period := range []domain.ListeningRange{domain.ListeningRange7D, domain.ListeningRangeAll} {
		insights, err := repo.GetInsights(ctx, period, now)
		if err != nil {
			t.Fatalf("get insights for %s: %v", period, err)
		}
		if insights.StreakDays != 10 {
			t.Fatalf("streak for %s = %d, want 10", period, insights.StreakDays)
		}
	}

	seedStreak(-1, -2)
	insights, err := repo.GetInsights(ctx, domain.ListeningRange7D, now)
	if err != nil {
		t.Fatalf("get insights with yesterday streak: %v", err)
	}
	if insights.StreakDays != 2 {
		t.Fatalf("yesterday streak = %d, want 2", insights.StreakDays)
	}

	seedStreak(-2)
	insights, err = repo.GetInsights(ctx, domain.ListeningRange7D, now)
	if err != nil {
		t.Fatalf("get insights with broken streak: %v", err)
	}
	if insights.StreakDays != 0 {
		t.Fatalf("broken streak = %d, want 0", insights.StreakDays)
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

func TestListeningRepositoryAggregatesPlaybackAttempts(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()
	ctx := context.Background()
	if err := NewTrackRepository(db).Save(ctx, &domain.Track{ID: "attempt-track", Path: "/attempt.flac", Title: "Attempt", SortTitle: "Attempt", Format: "flac"}); err != nil {
		t.Fatalf("save track: %v", err)
	}
	repo := NewListeningRepository(db)
	now := time.Date(2026, time.July, 24, 10, 0, 0, 0, time.Local)
	for i, reason := range []domain.PlaybackEndReason{domain.PlaybackEndCompleted, domain.PlaybackEndSkipped, domain.PlaybackEndStopped} {
		a := domain.PlaybackAttempt{ID: fmt.Sprintf("attempt-%d", i), TrackID: "attempt-track", StartedAt: now, StartPositionSeconds: 12}
		if err := repo.RecordAttemptStart(ctx, a); err != nil {
			t.Fatalf("start attempt: %v", err)
		}
		a.EndedAt, a.ListenedSeconds, a.EndReason = now.Add(time.Minute), 60, reason
		if err := repo.FinalizeAttempt(ctx, a); err != nil {
			t.Fatalf("finalize attempt: %v", err)
		}
	}
	insights, err := repo.GetInsights(ctx, domain.ListeningRange7D, now)
	if err != nil {
		t.Fatalf("GetInsights: %v", err)
	}
	if insights.Attempts != 3 || insights.Completed != 1 || insights.Skipped != 1 || insights.Stopped != 1 || insights.AverageSessionSeconds != 60 {
		t.Fatalf("attempt totals: %#v", insights)
	}
	if insights.CompletionRate == nil || insights.SkipRate == nil || *insights.CompletionRate != 100.0/3 || *insights.SkipRate != 100.0/3 {
		t.Fatalf("attempt rates: %#v", insights)
	}
}

func TestListeningRepositoryRecoversOpenPlaybackAttempts(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()
	ctx := context.Background()
	if err := NewTrackRepository(db).Save(ctx, &domain.Track{ID: "open-track", Path: "/open.flac", Title: "Open", SortTitle: "Open", Format: "flac"}); err != nil {
		t.Fatalf("save track: %v", err)
	}
	repo := NewListeningRepository(db)
	started := time.Now().Add(-time.Hour)
	if err := repo.RecordAttemptStart(ctx, domain.PlaybackAttempt{ID: "open", TrackID: "open-track", StartedAt: started}); err != nil {
		t.Fatalf("start attempt: %v", err)
	}
	if err := repo.RecoverOpenAttempts(ctx); err != nil {
		t.Fatalf("recover attempts: %v", err)
	}
	var reason string
	if err := db.Get(&reason, `SELECT end_reason FROM playback_attempts WHERE id = 'open'`); err != nil || reason != string(domain.PlaybackEndStopped) {
		t.Fatalf("recovered reason=%q err=%v", reason, err)
	}
	insights, err := repo.GetInsights(ctx, domain.ListeningRangeAll, time.Now())
	if err != nil {
		t.Fatalf("GetInsights: %v", err)
	}
	if insights.Stopped != 1 || insights.CompletionRate == nil || *insights.CompletionRate != 0 {
		t.Fatalf("recovered insights: %#v", insights)
	}
}

func TestListeningSnapshotMergeIsOriginAwareAndIdempotent(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()
	ctx := context.Background()
	if err := NewTrackRepository(db).Save(ctx, &domain.Track{ID: "synced", Path: "/synced.flac", Title: "Synced", SortTitle: "Synced", Format: "flac"}); err != nil {
		t.Fatal(err)
	}
	repo := NewListeningRepository(db)
	now := time.Now()
	snapshot := &domain.ListeningSyncSnapshot{
		Version:     1,
		Sessions:    []domain.ListeningSyncSession{{ID: "session", SourceDeviceID: "phone", TrackID: "synced", StartedAt: now.Add(-time.Minute).UnixMilli(), EndedAt: now.UnixMilli(), ListenedSeconds: 60, QualifiedPlay: true}},
		DailyTracks: []domain.DailyTrackListeningStat{{SourceDeviceID: "phone", LocalDate: now.Format("2006-01-02"), TrackID: "synced", ListenedSeconds: 60, PlayCount: 1}},
	}
	if err := repo.ImportSnapshot(ctx, snapshot); err != nil {
		t.Fatal(err)
	}
	if err := repo.ImportSnapshot(ctx, snapshot); err != nil {
		t.Fatal(err)
	}
	exported, err := repo.ExportSnapshot(ctx, "r", now.Add(-time.Hour))
	if err != nil {
		t.Fatal(err)
	}
	if len(exported.Sessions) != 1 || len(exported.DailyTracks) != 1 || exported.DailyTracks[0].PlayCount != 1 {
		t.Fatalf("unexpected export: %#v", exported)
	}
	var playCount int
	if err := db.Get(&playCount, `SELECT play_count FROM tracks WHERE id='synced'`); err != nil || playCount != 1 {
		t.Fatalf("play_count=%d err=%v", playCount, err)
	}
}
