package sqlite

import (
	"context"
	"log/slog"
	"os"
	"testing"
	"time"

	"airmedy/internal/domain"
)

func TestWatchedFolderRepository(t *testing.T) {
	dbPath := "test_watched_folder.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("Failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	repo := NewWatchedFolderRepository(db)

	folder := &domain.WatchedFolder{
		ID:        "folder-1",
		Path:      "/music/library",
		CreatedAt: time.Now(),
	}

	// Test Save
	err = repo.Save(ctx, folder)
	if err != nil {
		t.Fatalf("Failed to save watched folder: %v", err)
	}

	// Test GetByID
	saved, err := repo.GetByID(ctx, "folder-1")
	if err != nil {
		t.Fatalf("Failed to get watched folder: %v", err)
	}
	if saved == nil {
		t.Fatal("Expected saved folder, got nil")
	}
	if saved.Path != "/music/library" {
		t.Errorf("Expected path '/music/library', got '%s'", saved.Path)
	}

	// Test GetAll
	all, err := repo.GetAll(ctx)
	if err != nil {
		t.Fatalf("Failed to get all folders: %v", err)
	}
	if len(all) != 1 {
		t.Errorf("Expected 1 folder, got %d", len(all))
	}

	// Test Save (Update via ON CONFLICT)
	folder.CreatedAt = time.Now().Add(time.Hour)
	err = repo.Save(ctx, folder)
	if err != nil {
		t.Fatalf("Failed to update watched folder: %v", err)
	}

	// Test Delete
	err = repo.Delete(ctx, "folder-1")
	if err != nil {
		t.Fatalf("Failed to delete watched folder: %v", err)
	}

	deleted, _ := repo.GetByID(ctx, "folder-1")
	if deleted != nil {
		t.Error("Expected folder to be deleted")
	}
}
