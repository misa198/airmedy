package sqlite

import (
	"airmedy/internal/domain"
	"context"
	"log/slog"
	"os"
	"path/filepath"
	"reflect"
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

func TestTrayModeSettings(t *testing.T) {
	db, err := NewDB(filepath.Join(t.TempDir(), "tray-mode.db"), slog.Default())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })
	repo := NewSettingsRepository(db)
	ctx := context.Background()
	settings, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if settings.TrayMode != "menu" {
		t.Fatalf("default tray mode = %q", settings.TrayMode)
	}
	if settings.TrayTheme != "system" {
		t.Fatalf("default tray theme = %q", settings.TrayTheme)
	}
	settings.TrayTheme = "light"
	settings.TrayMode = "pill"
	if err := repo.Save(ctx, settings); err != nil {
		t.Fatal(err)
	}
	loaded, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.TrayTheme != "light" {
		t.Fatalf("saved tray theme = %q", loaded.TrayTheme)
	}
	if loaded.TrayMode != "pill" {
		t.Fatalf("saved tray mode = %q", loaded.TrayMode)
	}
	for _, direction := range []string{"down", "up"} {
		migration, err := os.ReadFile("migrations/000077_tray_mode." + direction + ".sql")
		if err != nil {
			t.Fatal(err)
		}
		if _, err := db.Exec(string(migration)); err != nil {
			t.Fatal(err)
		}
	}
	loaded, err = repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.TrayMode != "menu" {
		t.Fatalf("upgraded tray mode = %q", loaded.TrayMode)
	}
}

func TestPlayerBackgroundSettings(t *testing.T) {
	db, err := NewDB(filepath.Join(t.TempDir(), "backgrounds.db"), slog.Default())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })
	repo := NewSettingsRepository(db)
	ctx := context.Background()
	settings, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if !settings.LivingArtworkBackground || settings.MiniLivingArtworkBackground || settings.TrayBackgroundMode != "solid" {
		t.Fatalf("unexpected defaults: %+v", settings)
	}
	settings.LivingArtworkBackground = false
	settings.MiniLivingArtworkBackground = true
	settings.TrayBackgroundMode = "living"
	if err := repo.Save(ctx, settings); err != nil {
		t.Fatal(err)
	}
	loaded, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.LivingArtworkBackground || !loaded.MiniLivingArtworkBackground || loaded.TrayBackgroundMode != "living" {
		t.Fatalf("round trip: %+v", loaded)
	}
	for _, migrationName := range []string{"000080_tray_background_mode.down.sql", "000078_player_backgrounds.down.sql", "000078_player_backgrounds.up.sql", "000080_tray_background_mode.up.sql"} {
		migration, err := os.ReadFile("migrations/" + migrationName)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := db.Exec(string(migration)); err != nil {
			t.Fatal(err)
		}
	}
	loaded, err = repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.LivingArtworkBackground || loaded.MiniLivingArtworkBackground || loaded.TrayBackgroundMode != "solid" {
		t.Fatalf("upgrade: %+v", loaded)
	}
}

func TestTrayBackgroundModeMigration(t *testing.T) {
	db, err := NewDB(filepath.Join(t.TempDir(), "tray-background.db"), slog.Default())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })
	repo := NewSettingsRepository(db)
	ctx := context.Background()
	settings, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if settings.TrayBackgroundMode != "solid" {
		t.Fatalf("default: %q", settings.TrayBackgroundMode)
	}
	migrate := func(direction string) {
		t.Helper()
		sql, err := os.ReadFile("migrations/000080_tray_background_mode." + direction + ".sql")
		if err != nil {
			t.Fatal(err)
		}
		if _, err = db.Exec(string(sql)); err != nil {
			t.Fatal(err)
		}
	}
	for _, mode := range []string{"solid", "living", "monochrome"} {
		settings.TrayBackgroundMode = mode
		if err := repo.Save(ctx, settings); err != nil {
			t.Fatal(err)
		}
		loaded, err := repo.Load(ctx)
		if err != nil {
			t.Fatal(err)
		}
		if loaded.TrayBackgroundMode != mode {
			t.Fatalf("round trip: got %q, want %q", loaded.TrayBackgroundMode, mode)
		}
		migrate("down")
		var living bool
		if err := db.Get(&living, "SELECT tray_living_artwork_background FROM app_settings WHERE id = 1"); err != nil {
			t.Fatal(err)
		}
		if living != (mode == "living") {
			t.Fatalf("rollback %s: living=%v", mode, living)
		}
		migrate("up")
		loaded, err = repo.Load(ctx)
		if err != nil {
			t.Fatal(err)
		}
		expected := "solid"
		if living {
			expected = "living"
		}
		if loaded.TrayBackgroundMode != expected {
			t.Fatalf("upgrade: got %q, want %q", loaded.TrayBackgroundMode, expected)
		}
	}
}

func TestTitleBarSettings(t *testing.T) {
	db, err := NewDB(filepath.Join(t.TempDir(), "title-bar.db"), slog.Default())
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = db.Close() })
	repo := NewSettingsRepository(db)
	ctx := context.Background()
	settings, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if settings.TitleBarShowNavigation || !reflect.DeepEqual(settings.TitleBarActions, domain.DefaultTitleBarActions()) {
		t.Fatalf("unexpected defaults: %+v", settings)
	}
	settings.Language = "vi"
	settings.TitleBarShowNavigation = true
	settings.TitleBarActions[0], settings.TitleBarActions[4] = settings.TitleBarActions[4], settings.TitleBarActions[0]
	for i := range settings.TitleBarActions {
		settings.TitleBarActions[i].Visible = false
	}
	if err := repo.Save(ctx, settings); err != nil {
		t.Fatal(err)
	}
	loaded, err := repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if !loaded.TitleBarShowNavigation || !reflect.DeepEqual(loaded.TitleBarActions, settings.TitleBarActions) {
		t.Fatalf("round trip: %+v", loaded)
	}
	// Saving another field must retain hidden actions and their order.
	loaded.Theme = "black"
	if err := repo.Save(ctx, loaded); err != nil {
		t.Fatal(err)
	}
	loaded, err = repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(loaded.TitleBarActions, settings.TitleBarActions) {
		t.Fatal("unrelated save lost title bar settings")
	}
	for _, invalid := range []string{"invalid json", "[]", `[{"id":"unknown","visible":true}]`} {
		if _, err := db.Exec("UPDATE app_settings SET title_bar_actions = ? WHERE id = 1", invalid); err != nil {
			t.Fatal(err)
		}
		recovered, err := repo.Load(ctx)
		if err != nil {
			t.Fatal(err)
		}
		if !reflect.DeepEqual(recovered.TitleBarActions, domain.DefaultTitleBarActions()) || recovered.Language != "vi" || recovered.Theme != "black" {
			t.Fatal("invalid title bar actions should not reset other settings")
		}
	}

	for _, direction := range []string{"down", "up"} {
		migration, err := os.ReadFile("migrations/000081_title_bar." + direction + ".sql")
		if err != nil {
			t.Fatal(err)
		}
		if _, err := db.Exec(string(migration)); err != nil {
			t.Fatal(err)
		}
	}
	loaded, err = repo.Load(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.TitleBarShowNavigation || !reflect.DeepEqual(loaded.TitleBarActions, domain.DefaultTitleBarActions()) || loaded.Language != "vi" || loaded.Theme != "black" {
		t.Fatalf("upgrade: %+v", loaded)
	}
}
