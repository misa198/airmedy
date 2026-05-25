package sqlite

import (
	"context"
	"log/slog"
	"os"
	"testing"

	"airmedy/internal/domain"
)

func TestPlaylistRepository_AddTracks_NoPositionDuplicates(t *testing.T) {
	dbPath := "test_playlist.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	playlistRepo := NewPlaylistRepository(db)

	for i, id := range []string{"track-1", "track-2", "track-3"} {
		_ = i
		err := trackRepo.Save(ctx, &domain.Track{
			ID:        id,
			Path:      "/music/" + id + ".mp3",
			Title:     id,
			SortTitle: id,
			Format:    "mp3",
		})
		if err != nil {
			t.Fatalf("failed to save track %s: %v", id, err)
		}
	}

	playlist := &domain.Playlist{
		ID:   "pl-1",
		Name: "Test Playlist",
	}
	if err := playlistRepo.Save(ctx, playlist); err != nil {
		t.Fatalf("failed to save playlist: %v", err)
	}

	trackIDs := []string{"track-1", "track-2", "track-3"}
	if err := playlistRepo.AddTracks(ctx, "pl-1", trackIDs); err != nil {
		t.Fatalf("AddTracks failed: %v", err)
	}

	// Verify no duplicate positions
	var positions []string
	err = db.SelectContext(ctx, &positions, "SELECT position FROM playlist_tracks WHERE playlist_id = ? ORDER BY position", "pl-1")
	if err != nil {
		t.Fatalf("failed to query positions: %v", err)
	}

	if len(positions) != 3 {
		t.Errorf("expected 3 tracks, got %d", len(positions))
	}

	seen := make(map[string]bool)
	for _, pos := range positions {
		if seen[pos] {
			t.Errorf("duplicate position detected: %q", pos)
		}
		seen[pos] = true
	}
}

func TestPlaylistRepository_AddTracks_AppendsAfterExisting(t *testing.T) {
	dbPath := "test_playlist_append.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	playlistRepo := NewPlaylistRepository(db)

	for _, id := range []string{"track-1", "track-2", "track-3", "track-4"} {
		_ = trackRepo.Save(ctx, &domain.Track{
			ID:        id,
			Path:      "/music/" + id + ".mp3",
			Title:     id,
			SortTitle: id,
			Format:    "mp3",
		})
	}

	_ = playlistRepo.Save(ctx, &domain.Playlist{ID: "pl-2", Name: "Test"})

	// Add first track via single-track method
	maxPos, _ := playlistRepo.GetMaxPosition(ctx, "pl-2")
	_ = playlistRepo.AddTrack(ctx, "pl-2", "track-1", maxPos)

	// Batch add remaining tracks
	if err := playlistRepo.AddTracks(ctx, "pl-2", []string{"track-2", "track-3", "track-4"}); err != nil {
		t.Fatalf("AddTracks failed: %v", err)
	}

	var positions []string
	_ = db.SelectContext(ctx, &positions, "SELECT position FROM playlist_tracks WHERE playlist_id = ? ORDER BY position", "pl-2")

	if len(positions) != 4 {
		t.Errorf("expected 4 tracks, got %d", len(positions))
	}

	seen := make(map[string]bool)
	for _, pos := range positions {
		if seen[pos] {
			t.Errorf("duplicate position after batch append: %q", pos)
		}
		seen[pos] = true
	}
}
