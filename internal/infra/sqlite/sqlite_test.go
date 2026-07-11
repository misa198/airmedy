package sqlite

import (
	"context"
	"log/slog"
	"os"
	"testing"

	"airmedy/internal/domain"
)

func TestSqliteRepositories(t *testing.T) {
	dbPath := "test.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("Failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)

	track := &domain.Track{
		ID:            "test-1",
		Path:          "/path/to/test.mp3",
		Title:         "Test Track",
		SortTitle:     "Test Track",
		Copyright:     "Test Copyright",
		OtherMetadata: `{"test":"meta"}`,
		Format:        "mp3",
		AlbumID:       "",
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
	if savedTrackDTO.Copyright != "Test Copyright" {
		t.Errorf("Expected copyright 'Test Copyright', got '%s'", savedTrackDTO.Copyright)
	}
	if savedTrackDTO.OtherMetadata != `{"test":"meta"}` {
		t.Errorf("Expected other_metadata '{\"test\":\"meta\"}', got '%s'", savedTrackDTO.OtherMetadata)
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

	// Test Album Copyright
	albumRepo := NewAlbumRepository(db)
	album := &domain.Album{
		ID:        "test-album-1",
		Title:     "Test Album",
		SortTitle: "Test Album",
		Copyright: "Album Copyright",
	}
	err = albumRepo.Save(ctx, album)
	if err != nil {
		t.Fatalf("Failed to save album: %v", err)
	}

	savedAlbumDTO, err := albumRepo.GetByID(ctx, "test-album-1")
	if err != nil {
		t.Fatalf("Failed to get album: %v", err)
	}
	if savedAlbumDTO.Copyright != "Album Copyright" {
		t.Errorf("Expected album copyright 'Album Copyright', got '%s'", savedAlbumDTO.Copyright)
	}

	// Test SettingsRepository
	settingsRepo := NewSettingsRepository(db)
	settings := &domain.AppSettings{
		Language:  "fr",
		Theme:     "dark",
		EQEnabled: false,
		EQPreamp:  -3.5,
	}
	err = settingsRepo.Save(ctx, settings)
	if err != nil {
		t.Fatalf("Failed to save settings: %v", err)
	}

	savedSettings, err := settingsRepo.Load(ctx)
	if err != nil {
		t.Fatalf("Failed to load settings: %v", err)
	}
	if savedSettings.Language != "fr" {
		t.Errorf("Expected language 'fr', got '%s'", savedSettings.Language)
	}
	if savedSettings.Theme != "dark" {
		t.Errorf("Expected theme 'dark', got '%s'", savedSettings.Theme)
	}
	if savedSettings.EQEnabled != false {
		t.Errorf("Expected EQEnabled false, got %v", savedSettings.EQEnabled)
	}
	if savedSettings.EQPreamp != -3.5 {
		t.Errorf("Expected EQPreamp -3.5, got %v", savedSettings.EQPreamp)
	}

	var profilePreampColumns int
	if err := db.Get(&profilePreampColumns, `SELECT COUNT(*) FROM pragma_table_info('eq_profiles') WHERE name = 'preamp_gain'`); err != nil {
		t.Fatalf("failed to inspect eq_profiles schema: %v", err)
	}
	if profilePreampColumns != 0 {
		t.Errorf("expected eq_profiles to contain only profile metadata and bands, found preamp_gain")
	}
	var profileKeyColumns int
	if err := db.Get(&profileKeyColumns, `SELECT COUNT(*) FROM pragma_table_info('eq_profiles') WHERE name = 'preset_key'`); err != nil {
		t.Fatalf("failed to inspect eq_profiles key schema: %v", err)
	}
	if profileKeyColumns != 1 {
		t.Errorf("expected eq_profiles.preset_key column, got %d", profileKeyColumns)
	}
}

func TestTrackFeaturesMigration(t *testing.T) {
	dbPath := "test_features.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("Failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	// track_features table exists with expected analyzer columns.
	var count int
	if err := db.Get(&count, `SELECT COUNT(*) FROM pragma_table_info('track_features') WHERE name IN ('loudness_lufs','spectral_centroid','tempo','onset_variance')`); err != nil {
		t.Fatalf("track_features table not created: %v", err)
	}
	if count != 4 {
		t.Errorf("Expected 4 known track_features columns, got %d", count)
	}

	// tracks.analyzed_version pending marker exists.
	if err := db.Get(&count, `SELECT COUNT(*) FROM pragma_table_info('tracks') WHERE name = 'analyzed_version'`); err != nil || count != 1 {
		t.Errorf("tracks.analyzed_version missing (count=%d, err=%v)", count, err)
	}
}

func TestAnalysisComponentVersionsMigration(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()
	var count int
	if err := db.Get(&count, `SELECT COUNT(*) FROM pragma_table_info('track_analysis_components') WHERE name IN ('track_id', 'component', 'version', 'status')`); err != nil {
		t.Fatal(err)
	}
	if count != 4 {
		t.Fatalf("component analysis table columns: got %d want 4", count)
	}
	if err := db.Get(&count, `SELECT COUNT(*) FROM pragma_index_list('tracks') WHERE name = 'idx_tracks_analysis_pending_backfill'`); err != nil {
		t.Fatal(err)
	}
	if count != 1 {
		t.Fatalf("pending analysis backfill index missing (count=%d)", count)
	}
}

func TestAnalysisComponentVersionsMigrationBackfillsOnlyV4(t *testing.T) {
	db, err := NewDB(":memory:", slog.Default())
	if err != nil {
		t.Fatalf("NewDB: %v", err)
	}
	defer func() { _ = db.Close() }()
	ctx := context.Background()
	tracks := NewTrackRepository(db)
	for _, id := range []string{"current", "failed", "old"} {
		if err := tracks.Save(ctx, &domain.Track{ID: id, Path: "/m/" + id, Title: id, Format: "mp3"}); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := db.Exec(`UPDATE tracks SET analyzed_version = 4 WHERE id IN ('current', 'failed')`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`UPDATE tracks SET analyzed_version = 3 WHERE id = 'old'`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`INSERT INTO track_features (track_id, analyzer_version) VALUES ('current', 4)`); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`DROP TABLE track_analysis_components`); err != nil {
		t.Fatal(err)
	}
	migration, err := os.ReadFile("migrations/000051_analysis_component_versions.up.sql")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(string(migration)); err != nil {
		t.Fatal(err)
	}
	var currentComplete, failedRows, oldRows int
	if err := db.Get(&currentComplete, `SELECT COUNT(*) FROM track_analysis_components WHERE track_id = 'current' AND version = 1 AND status = 'complete'`); err != nil {
		t.Fatal(err)
	}
	if err := db.Get(&failedRows, `SELECT COUNT(*) FROM track_analysis_components WHERE track_id = 'failed' AND version = 1 AND status = 'failed'`); err != nil {
		t.Fatal(err)
	}
	if err := db.Get(&oldRows, `SELECT COUNT(*) FROM track_analysis_components WHERE track_id = 'old'`); err != nil {
		t.Fatal(err)
	}
	if currentComplete != 2 || failedRows != 2 || oldRows != 0 {
		t.Fatalf("unexpected backfill: current=%d failed=%d old=%d", currentComplete, failedRows, oldRows)
	}
}

func TestNormalizationSettingsRoundTrip(t *testing.T) {
	dbPath := "test_norm.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("Failed to create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	repo := NewSettingsRepository(db)

	// Empty table => normalization defaults.
	def, err := repo.Load(ctx)
	if err != nil {
		t.Fatalf("Failed to load default settings: %v", err)
	}
	if def.NormalizationEnabled != false || def.NormalizationMode != "track" ||
		def.NormalizationTargetLUFS != domain.DefaultTargetLUFS || def.NormalizationPreventClip != true {
		t.Errorf("Unexpected normalization defaults: %+v", def)
	}
	if def.LibraryAnalysisEnabled != false {
		t.Errorf("Expected LibraryAnalysisEnabled default false, got %+v", def)
	}
	if def.HighContrastLyrics != true {
		t.Errorf("Expected HighContrastLyrics default true, got %+v", def)
	}

	// Non-default values round-trip.
	in := &domain.AppSettings{
		Language:                 "en",
		Theme:                    "dark",
		LibraryAnalysisEnabled:   true,
		NormalizationEnabled:     true,
		NormalizationMode:        "album",
		NormalizationTargetLUFS:  -18,
		NormalizationPreventClip: false,
		HighContrastLyrics:       false,
	}
	if err := repo.Save(ctx, in); err != nil {
		t.Fatalf("Failed to save settings: %v", err)
	}
	out, err := repo.Load(ctx)
	if err != nil {
		t.Fatalf("Failed to load settings: %v", err)
	}
	if out.NormalizationEnabled != true || out.NormalizationMode != "album" ||
		out.NormalizationTargetLUFS != -18 || out.NormalizationPreventClip != false {
		t.Errorf("Normalization round-trip mismatch: %+v", out)
	}
	if out.LibraryAnalysisEnabled != true {
		t.Errorf("LibraryAnalysisEnabled round-trip mismatch: %+v", out)
	}
	if out.HighContrastLyrics != false {
		t.Errorf("HighContrastLyrics round-trip mismatch: %+v", out)
	}
}
