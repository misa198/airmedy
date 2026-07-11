package sqlite

import (
	"context"
	"log/slog"
	"os"
	"testing"
	"time"

	"airmedy/internal/app/playlist"
	"airmedy/internal/domain"
)

func TestTrackRepository_GetByRules(t *testing.T) {
	dbPath := "test_smart_rules.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)

	tracks := []*domain.Track{
		{ID: "t1", Path: "/m/t1.mp3", Title: "Rock 90s", SortTitle: "rock 90s", Format: "mp3", Year: 1995},
		{ID: "t2", Path: "/m/t2.mp3", Title: "Rock 2000s", SortTitle: "rock 2000s", Format: "mp3", Year: 2005},
		{ID: "t3", Path: "/m/t3.mp3", Title: "Jazz 90s", SortTitle: "jazz 90s", Format: "mp3", Year: 1995},
	}
	for _, tr := range tracks {
		if err := trackRepo.Save(ctx, tr); err != nil {
			t.Fatalf("failed to save track %s: %v", tr.ID, err)
		}
	}

	if _, err := db.ExecContext(ctx, "INSERT INTO genres (id, name) VALUES ('g-rock', 'Rock'), ('g-jazz', 'Jazz')"); err != nil {
		t.Fatalf("failed to seed genres: %v", err)
	}
	if _, err := db.ExecContext(ctx, "INSERT INTO track_genres (track_id, genre_id, position) VALUES ('t1', 'g-rock', 0), ('t2', 'g-rock', 0), ('t3', 'g-jazz', 0)"); err != nil {
		t.Fatalf("failed to seed track_genres: %v", err)
	}

	// year between 1990-1999
	where, args, err := playlist.BuildWhereClause(domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{
			{Field: "year", Op: "between", Value: []any{1990.0, 1999.0}},
		},
	})
	if err != nil {
		t.Fatalf("build where: %v", err)
	}
	got, err := trackRepo.GetByRules(ctx, where, args, 0, "")
	if err != nil {
		t.Fatalf("GetByRules: %v", err)
	}
	if len(got) != 2 {
		t.Fatalf("expected 2 tracks in 1990-1999, got %d", len(got))
	}

	// genre is Rock AND year between 1990-1999 -> only t1
	where, args, err = playlist.BuildWhereClause(domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{
			{Field: "genre", Op: "is", Value: "Rock"},
			{Field: "year", Op: "between", Value: []any{1990.0, 1999.0}},
		},
	})
	if err != nil {
		t.Fatalf("build where: %v", err)
	}
	got, err = trackRepo.GetByRules(ctx, where, args, 0, "")
	if err != nil {
		t.Fatalf("GetByRules: %v", err)
	}
	if len(got) != 1 || got[0].ID != "t1" {
		t.Fatalf("expected only t1, got %+v", got)
	}

	// nested groups: (genre is Rock AND year between 1990-1999) OR (genre is Jazz)
	// -> t1 (Rock/1995) and t3 (Jazz/1995)
	where, args, err = playlist.BuildWhereClause(domain.SmartRuleGroup{
		Match: "any",
		Groups: []domain.SmartRuleGroup{
			{
				Match: "all",
				Rules: []domain.SmartRule{
					{Field: "genre", Op: "is", Value: "Rock"},
					{Field: "year", Op: "between", Value: []any{1990.0, 1999.0}},
				},
			},
			{
				Match: "all",
				Rules: []domain.SmartRule{
					{Field: "genre", Op: "is", Value: "Jazz"},
				},
			},
		},
	})
	if err != nil {
		t.Fatalf("build where: %v", err)
	}
	got, err = trackRepo.GetByRules(ctx, where, args, 0, "")
	if err != nil {
		t.Fatalf("GetByRules: %v", err)
	}
	if len(got) != 2 {
		t.Fatalf("expected 2 tracks (t1, t3) from nested groups, got %d: %+v", len(got), got)
	}

	// limit: all tracks, ordered by title, capped to 1 -> "Jazz 90s" sorts first
	where, args, err = playlist.BuildWhereClause(domain.SmartRuleGroup{Match: "all"})
	if err != nil {
		t.Fatalf("build where: %v", err)
	}
	orderBy, err := playlist.OrderBySQL("title")
	if err != nil {
		t.Fatalf("order by: %v", err)
	}
	got, err = trackRepo.GetByRules(ctx, where, args, 1, orderBy)
	if err != nil {
		t.Fatalf("GetByRules: %v", err)
	}
	if len(got) != 1 || got[0].ID != "t3" {
		t.Fatalf("expected only t3 (sorts first by title), got %+v", got)
	}

	// mood: t1 gets high energy/danceability, t2 gets low, t3 is left
	// unanalyzed (no track_features row at all) to confirm it's excluded.
	if _, err := db.ExecContext(ctx, `
		INSERT INTO track_features (track_id, energy, danceability) VALUES
			('t1', 0.9, 0.8),
			('t2', 0.1, 0.2)
	`); err != nil {
		t.Fatalf("failed to seed track_features: %v", err)
	}
	where, args, err = playlist.BuildWhereClause(domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{
			{Field: "energy", Op: "between", Value: []any{0.6, 1.0}},
			{Field: "danceability", Op: "between", Value: []any{0.6, 1.0}},
		},
	})
	if err != nil {
		t.Fatalf("build where: %v", err)
	}
	got, err = trackRepo.GetByRules(ctx, where, args, 0, "")
	if err != nil {
		t.Fatalf("GetByRules: %v", err)
	}
	if len(got) != 1 || got[0].ID != "t1" {
		t.Fatalf("expected only t1 (high energy/danceability), got %+v", got)
	}

	if _, err := db.ExecContext(ctx, `UPDATE track_features SET brightness = 0.7 WHERE track_id = 't1'`); err != nil {
		t.Fatalf("failed to seed brightness: %v", err)
	}
	where, args, err = playlist.BuildWhereClause(domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "brightness", Op: "between", Value: []any{0.6, 0.8}}},
	})
	if err != nil {
		t.Fatalf("build brightness where: %v", err)
	}
	got, err = trackRepo.GetByRules(ctx, where, args, 0, "")
	if err != nil {
		t.Fatalf("GetByRules brightness: %v", err)
	}
	if len(got) != 1 || got[0].ID != "t1" {
		t.Fatalf("expected only t1 (matching brightness; NULL is excluded), got %+v", got)
	}

	// added_at: t1/t2/t3 all just saved (now), t4 backdated 60 days — the
	// sargable rewrite (t.created_at >= ?) must exclude it for a 30-day window.
	old := &domain.Track{ID: "t4", Path: "/m/t4.mp3", Title: "Old Track", SortTitle: "old track", Format: "mp3", CreatedAt: time.Now().Add(-60 * 24 * time.Hour)}
	if err := trackRepo.Save(ctx, old); err != nil {
		t.Fatalf("failed to save backdated track: %v", err)
	}
	where, args, err = playlist.BuildWhereClause(domain.SmartRuleGroup{
		Match: "all",
		Rules: []domain.SmartRule{{Field: "added_at", Op: "in_last_days", Value: 30.0}},
	})
	if err != nil {
		t.Fatalf("build where: %v", err)
	}
	got, err = trackRepo.GetByRules(ctx, where, args, 0, "")
	if err != nil {
		t.Fatalf("GetByRules: %v", err)
	}
	for _, tr := range got {
		if tr.ID == "t4" {
			t.Fatalf("expected backdated track t4 excluded from in_last_days=30, got %+v", got)
		}
	}
	if len(got) != 3 {
		t.Fatalf("expected t1,t2,t3 (recently added) and not t4, got %d: %+v", len(got), got)
	}
}
