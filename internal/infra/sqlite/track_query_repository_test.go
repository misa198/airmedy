package sqlite

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"testing"

	"airmedy/internal/domain"
)

func TestTrackQueryRepository_MoodDensityGrid(t *testing.T) {
	dbPath := "test_mood_density_grid.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	queryRepo := NewTrackQueryRepository(db, trackRepo)

	tracks := []*domain.Track{
		{ID: "t1", Path: "/m/t1.mp3", Title: "T1", SortTitle: "t1", Format: "mp3"},
		{ID: "t2", Path: "/m/t2.mp3", Title: "T2", SortTitle: "t2", Format: "mp3"},
		{ID: "t3", Path: "/m/t3.mp3", Title: "T3", SortTitle: "t3", Format: "mp3"},
		{ID: "t4", Path: "/m/t4.mp3", Title: "T4", SortTitle: "t4", Format: "mp3"},
	}
	for _, tr := range tracks {
		if err := trackRepo.Save(ctx, tr); err != nil {
			t.Fatalf("failed to save track %s: %v", tr.ID, err)
		}
	}

	// t1, t2 land in the same bucket (grid=10 -> bucket 9,9); t3 in a distinct
	// low bucket; t4 has no track_features row at all (unanalyzed).
	if _, err := db.ExecContext(ctx, `
		INSERT INTO track_features (track_id, energy, danceability) VALUES
			('t1', 0.95, 0.91),
			('t2', 0.99, 0.99),
			('t3', 0.05, 0.02)
	`); err != nil {
		t.Fatalf("failed to seed track_features: %v", err)
	}

	grid, err := queryRepo.MoodDensityGrid(ctx, 10)
	if err != nil {
		t.Fatalf("MoodDensityGrid: %v", err)
	}
	if grid.GridSize != 10 {
		t.Fatalf("expected grid size 10, got %d", grid.GridSize)
	}
	if grid.TotalCount != 4 {
		t.Fatalf("expected total count 4, got %d", grid.TotalCount)
	}
	if grid.AnalyzedCount != 3 {
		t.Fatalf("expected analyzed count 3 (t4 excluded, no row), got %d", grid.AnalyzedCount)
	}
	if got := grid.Counts[9][9]; got != 2 {
		t.Fatalf("expected bucket [9][9] (high energy/dance) to hold t1+t2 = 2, got %d", got)
	}
	if got := grid.Counts[0][0]; got != 1 {
		t.Fatalf("expected bucket [0][0] (low energy/dance) to hold t3 = 1, got %d", got)
	}
	// A cell with no tracks should be exactly zero, not some interpolated value.
	if got := grid.Counts[5][5]; got != 0 {
		t.Fatalf("expected empty bucket [5][5] to be 0, got %d", got)
	}

	sum := 0
	for x := range grid.Counts {
		for y := range grid.Counts[x] {
			sum += grid.Counts[x][y]
		}
	}
	if sum != grid.AnalyzedCount {
		t.Fatalf("sum of bucket counts (%d) should equal AnalyzedCount (%d)", sum, grid.AnalyzedCount)
	}
}

func TestTrackQueryRepository_FindSimilarExcludesQueuedTracksBeforeLimit(t *testing.T) {
	dbPath := "test_find_similar_exclusions.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	queryRepo := NewTrackQueryRepository(db, trackRepo)
	for _, track := range []*domain.Track{
		{ID: "seed", Path: "/m/seed.mp3", Title: "Seed", SortTitle: "seed", Format: "mp3"},
		{ID: "closest", Path: "/m/closest.mp3", Title: "Closest", SortTitle: "closest", Format: "mp3"},
		{ID: "next", Path: "/m/next.mp3", Title: "Next", SortTitle: "next", Format: "mp3"},
		{ID: "far", Path: "/m/far.mp3", Title: "Far", SortTitle: "far", Format: "mp3"},
	} {
		if err := trackRepo.Save(ctx, track); err != nil {
			t.Fatalf("failed to save track %s: %v", track.ID, err)
		}
	}
	if _, err := db.ExecContext(ctx, `
		INSERT INTO track_features (track_id, energy, danceability, tempo) VALUES
			('seed', 0.50, 0.50, 120),
			('closest', 0.51, 0.50, 120),
			('next', 0.55, 0.50, 120),
			('far', 0.90, 0.90, 180)
	`); err != nil {
		t.Fatalf("failed to seed track features: %v", err)
	}

	tracks, err := queryRepo.FindSimilar(ctx, "seed", []string{"closest"}, 2)
	if err != nil {
		t.Fatalf("FindSimilar: %v", err)
	}
	if len(tracks) != 2 || tracks[0].ID != "next" || tracks[1].ID != "far" {
		t.Fatalf("expected excluded closest track to be skipped before LIMIT, got %#v", trackIDs(tracks))
	}
}

func TestTrackQueryRepository_FindSimilarSupportsLargeExclusionLists(t *testing.T) {
	dbPath := "test_find_similar_large_exclusions.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	queryRepo := NewTrackQueryRepository(db, trackRepo)
	for _, track := range []*domain.Track{
		{ID: "seed", Path: "/m/seed.mp3", Title: "Seed", SortTitle: "seed", Format: "mp3"},
		{ID: "closest", Path: "/m/closest.mp3", Title: "Closest", SortTitle: "closest", Format: "mp3"},
		{ID: "next", Path: "/m/next.mp3", Title: "Next", SortTitle: "next", Format: "mp3"},
	} {
		if err := trackRepo.Save(ctx, track); err != nil {
			t.Fatalf("failed to save track %s: %v", track.ID, err)
		}
	}
	if _, err := db.ExecContext(ctx, `
		INSERT INTO track_features (track_id, energy, danceability, tempo) VALUES
			('seed', 0.50, 0.50, 120),
			('closest', 0.51, 0.50, 120),
			('next', 0.55, 0.50, 120)
	`); err != nil {
		t.Fatalf("failed to seed track features: %v", err)
	}

	excluded := make([]string, 0, 3001)
	for i := 0; i < 3000; i++ {
		excluded = append(excluded, fmt.Sprintf("queued-%d", i))
	}
	excluded = append(excluded, "closest")

	tracks, err := queryRepo.FindSimilar(ctx, "seed", excluded, 1)
	if err != nil {
		t.Fatalf("FindSimilar with 3,001 exclusions: %v", err)
	}
	if len(tracks) != 1 || tracks[0].ID != "next" {
		t.Fatalf("expected next track after excluding closest, got %#v", trackIDs(tracks))
	}
}

func trackIDs(tracks []*domain.TrackDTO) []string {
	ids := make([]string, len(tracks))
	for i, track := range tracks {
		ids[i] = track.ID
	}
	return ids
}
