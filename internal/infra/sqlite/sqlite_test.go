package sqlite

import (
	"context"
	"log/slog"
	"os"
	"testing"

	"changeme/internal/domain"
)

func TestSqliteRepositories(t *testing.T) {
	dbPath := "test.db"
	defer os.Remove(dbPath)

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("Failed to create test db: %v", err)
	}
	defer db.Close()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)

	track := &domain.Track{
		ID:        "test-1",
		Path:      "/path/to/test.mp3",
		Title:     "Test Track",
		SortTitle: "Test Track",
		Format:    "mp3",
		AlbumID:   "",
	}

	err = trackRepo.Save(ctx, track)
	if err != nil {
		t.Fatalf("Failed to save track: %v", err)
	}

	savedTrackDTO, err := trackRepo.GetByID(ctx, "test-1")
	if err != nil {
		t.Fatalf("Failed to get track: %v", err)
	}
	if savedTrackDTO.Title != "Test Track" {
		t.Errorf("Expected title 'Test Track', got '%s'", savedTrackDTO.Title)
	}

	// Test Upsert
	track.Title = "Updated Track"
	err = trackRepo.Upsert(ctx, track)
	if err != nil {
		t.Fatalf("Failed to upsert track: %v", err)
	}

	updatedTrackDTO, _ := trackRepo.GetByID(ctx, "test-1")
	if updatedTrackDTO.Title != "Updated Track" {
		t.Errorf("Expected title 'Updated Track', got '%s'", updatedTrackDTO.Title)
	}
}
