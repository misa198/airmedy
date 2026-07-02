package sqlite

import (
	"context"
	"log/slog"
	"os"
	"testing"
	"time"

	"airmedy/internal/domain"
)

func TestAnalysisRepository(t *testing.T) {
	dbPath := "analysis_test.db"
	defer func() { _ = os.Remove(dbPath) }()

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	repo := NewAnalysisRepository(db)

	// Seed two tracks (both pending: analyzed_version defaults to 0).
	for _, id := range []string{"trk-1", "trk-2"} {
		if err := trackRepo.Save(ctx, &domain.Track{ID: id, Path: "/m/" + id + ".mp3", Title: id, Format: "mp3"}); err != nil {
			t.Fatalf("save track %s: %v", id, err)
		}
	}

	const version = 1

	// No features yet.
	if got, err := repo.GetFeatures(ctx, "trk-1"); err != nil || got != nil {
		t.Fatalf("GetFeatures empty: got %v, err %v", got, err)
	}
	if n, err := repo.CountPending(ctx, version); err != nil || n != 2 {
		t.Fatalf("CountPending before: got %d, err %v", n, err)
	}
	if ids, err := repo.ListPending(ctx, version, 10); err != nil || len(ids) != 2 {
		t.Fatalf("ListPending before: got %v, err %v", ids, err)
	}

	feat := &domain.TrackFeatures{
		TrackID:          "trk-1",
		AnalyzerVersion:  version,
		AnalyzedAt:       time.Now().UTC().Truncate(time.Second),
		LoudnessLUFS:     -14.2,
		LoudnessRange:    7.5,
		TruePeak:         -1.1,
		RMS:              -18.3,
		Crest:            12.0,
		SpectralCentroid: 2200.0,
		SpectralRolloff:  4800.0,
		SpectralFlatness: 0.15,
		SpectralFlux:     0.42,
		ZCR:              0.08,
		Tempo:            128.0,
	}
	if err := repo.UpsertFeatures(ctx, feat); err != nil {
		t.Fatalf("UpsertFeatures: %v", err)
	}

	got, err := repo.GetFeatures(ctx, "trk-1")
	if err != nil || got == nil {
		t.Fatalf("GetFeatures after upsert: got %v, err %v", got, err)
	}
	if got.LoudnessLUFS != feat.LoudnessLUFS || got.TruePeak != feat.TruePeak || got.SpectralFlux != feat.SpectralFlux {
		t.Errorf("round-trip mismatch: %+v vs %+v", got, feat)
	}
	if got.Tempo != feat.Tempo {
		t.Errorf("tempo round-trip: got %v want %v", got.Tempo, feat.Tempo)
	}
	if got.AnalyzerVersion != version {
		t.Errorf("analyzer version: got %d want %d", got.AnalyzerVersion, version)
	}

	// trk-1 no longer pending; trk-2 still is.
	if n, err := repo.CountPending(ctx, version); err != nil || n != 1 {
		t.Fatalf("CountPending after: got %d, err %v", n, err)
	}
	if ids, err := repo.ListPending(ctx, version, 10); err != nil || len(ids) != 1 || ids[0] != "trk-2" {
		t.Fatalf("ListPending after: got %v, err %v", ids, err)
	}

	// Idempotent re-upsert with new values updates in place (no duplicate row).
	feat.LoudnessLUFS = -10.0
	if err := repo.UpsertFeatures(ctx, feat); err != nil {
		t.Fatalf("re-upsert: %v", err)
	}
	got, _ = repo.GetFeatures(ctx, "trk-1")
	if got.LoudnessLUFS != -10.0 {
		t.Errorf("re-upsert value: got %v want -10", got.LoudnessLUFS)
	}
	if n, _ := repo.CountPending(ctx, version); n != 1 {
		t.Errorf("CountPending stable after re-upsert: got %d want 1", n)
	}

	// A higher analyzer version makes the analyzed track pending again.
	if n, err := repo.CountPending(ctx, version+1); err != nil || n != 2 {
		t.Fatalf("CountPending higher version: got %d, err %v", n, err)
	}

	// ListPending honours the limit.
	if ids, _ := repo.ListPending(ctx, version+1, 1); len(ids) != 1 {
		t.Errorf("ListPending limit: got %v want len 1", ids)
	}
}
