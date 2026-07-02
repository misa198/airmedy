package analysis

import (
	"context"
	"log/slog"
	"os"
	"testing"
	"time"

	"airmedy/internal/domain"
	"airmedy/internal/infra/sqlite"
)

// canningAnalyzer returns a fixed, valid TrackFeatures result without
// touching ffmpeg/cgo, so this test runs everywhere.
type canningAnalyzer struct{}

func (canningAnalyzer) Analyze(ctx context.Context, path string) (*domain.TrackFeatures, error) {
	return &domain.TrackFeatures{LoudnessLUFS: -14.0}, nil
}

func TestAnalysisServiceBackfillDrainsPendingTracks(t *testing.T) {
	dbPath := "analysis_service_test.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := sqlite.NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := sqlite.NewTrackRepository(db)
	analysisRepo := sqlite.NewAnalysisRepository(db)

	for _, id := range []string{"trk-1", "trk-2", "trk-3"} {
		if err := trackRepo.Save(ctx, &domain.Track{ID: id, Path: "/m/" + id + ".mp3", Title: id, Format: "mp3"}); err != nil {
			t.Fatalf("save track %s: %v", id, err)
		}
	}

	settingsRepo := sqlite.NewSettingsRepository(db)
	if err := settingsRepo.Save(ctx, &domain.AppSettings{LibraryAnalysisEnabled: true}); err != nil {
		t.Fatalf("seed settings: %v", err)
	}
	svc := NewAnalysisService(trackRepo, analysisRepo, canningAnalyzer{}, settingsRepo, slog.Default())
	if err := svc.Start(ctx); err != nil {
		t.Fatalf("Start: %v", err)
	}
	defer func() { _ = svc.Stop(context.Background()) }()

	waitFor(t, 5*time.Second, func() bool {
		n, err := analysisRepo.CountPending(ctx, analyzerVersion)
		return err == nil && n == 0
	})

	for _, id := range []string{"trk-1", "trk-2", "trk-3"} {
		feat, err := analysisRepo.GetFeatures(ctx, id)
		if err != nil || feat == nil {
			t.Fatalf("GetFeatures(%s): got %v, err %v", id, feat, err)
		}
		if feat.AnalyzerVersion != analyzerVersion {
			t.Errorf("track %s: analyzer version got %d want %d", id, feat.AnalyzerVersion, analyzerVersion)
		}
		if feat.LoudnessLUFS != -14.0 {
			t.Errorf("track %s: loudness got %v want -14.0", id, feat.LoudnessLUFS)
		}
	}
}

func TestAnalysisServiceEnqueueAfterStart(t *testing.T) {
	dbPath := "analysis_service_enqueue_test.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := sqlite.NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := sqlite.NewTrackRepository(db)
	analysisRepo := sqlite.NewAnalysisRepository(db)

	settingsRepo := sqlite.NewSettingsRepository(db)
	if err := settingsRepo.Save(ctx, &domain.AppSettings{LibraryAnalysisEnabled: true}); err != nil {
		t.Fatalf("seed settings: %v", err)
	}
	svc := NewAnalysisService(trackRepo, analysisRepo, canningAnalyzer{}, settingsRepo, slog.Default())
	if err := svc.Start(ctx); err != nil {
		t.Fatalf("Start: %v", err)
	}
	defer func() { _ = svc.Stop(context.Background()) }()

	// Simulate the import-time enqueue hook firing for a track that didn't
	// exist at Start (mirrors LibraryService.AddAnalysisListener wiring).
	if err := trackRepo.Save(ctx, &domain.Track{ID: "trk-new", Path: "/m/trk-new.mp3", Title: "new", Format: "mp3"}); err != nil {
		t.Fatalf("save track: %v", err)
	}
	svc.Enqueue("trk-new", false)

	waitFor(t, 5*time.Second, func() bool {
		feat, err := analysisRepo.GetFeatures(ctx, "trk-new")
		return err == nil && feat != nil
	})
}
