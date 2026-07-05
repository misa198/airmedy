package sqlite

import (
	"context"
	"log/slog"
	"os"
	"testing"

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
}
