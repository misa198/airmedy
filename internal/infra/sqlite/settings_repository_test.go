package sqlite

import (
	"context"
	"log/slog"
	"os"
	"path/filepath"
	"testing"
)

func TestPlayerFavoriteButtonSettings(t *testing.T) {
	db, err := NewDB(filepath.Join(t.TempDir(), "settings.db"), slog.Default())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })
	ctx := context.Background()
	repo := NewSettingsRepository(db)
	settings, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if settings.ShowPlayerFavoriteButton {
		t.Fatal("favorite button should be hidden by default")
	}
	settings.Language = "vi"
	for _, enabled := range []bool{true, false, true} {
		settings.ShowPlayerFavoriteButton = enabled
		if err := repo.Save(ctx, settings); err != nil {
			t.Fatal(err)
		}
		loaded, err := repo.Load(ctx)
		if err != nil {
			t.Fatal(err)
		}
		if loaded.ShowPlayerFavoriteButton != enabled {
			t.Fatalf("favorite button: got %v, want %v", loaded.ShowPlayerFavoriteButton, enabled)
		}
	}

	// Recreate the upgrade from an existing settings row and verify the down path.
	for _, direction := range []string{"down", "up"} {
		migration, err := os.ReadFile("migrations/000076_show_player_favorite_button." + direction + ".sql")
		if err != nil {
			t.Fatal(err)
		}
		if _, err := db.Exec(string(migration)); err != nil {
			t.Fatal(err)
		}
	}
	loaded, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.ShowPlayerFavoriteButton || loaded.Language != "vi" {
		t.Fatal("upgrade should hide the favorite button and preserve existing settings")
	}
}
