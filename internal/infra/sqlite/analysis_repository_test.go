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

func TestAnalysisRepositoryComponentVersions(t *testing.T) {
	dbPath := "analysis_components_test.db"
	defer func() { _ = os.Remove(dbPath) }()
	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("create test db: %v", err)
	}
	defer func() { _ = db.Close() }()

	ctx := context.Background()
	trackRepo := NewTrackRepository(db)
	repo, ok := NewAnalysisRepository(db).(domain.ComponentAnalysisRepository)
	if !ok {
		t.Fatal("analysis repository must implement component analysis repository")
	}
	for _, id := range []string{"both", "aubio-only"} {
		if err := trackRepo.Save(ctx, &domain.Track{ID: id, Path: "/m/" + id, Title: id, Format: "mp3"}); err != nil {
			t.Fatal(err)
		}
	}
	required := map[domain.AnalysisComponents]int{domain.AnalysisComponentFFmpeg: 1, domain.AnalysisComponentAubio: 1}
	if pending, err := repo.CountPendingComponentTracks(ctx, required); err != nil || pending != 2 {
		t.Fatalf("initial component pending: got %d, err %v", pending, err)
	}
	feat := &domain.TrackFeatures{TrackID: "both", AnalyzedAt: time.Now().UTC(), LoudnessLUFS: -14, Tempo: 120}
	if err := repo.UpsertComponentFeatures(ctx, feat, domain.AnalysisComponentsAll, required); err != nil {
		t.Fatal(err)
	}
	if pending, err := repo.PendingComponents(ctx, "both", required); err != nil || pending != 0 {
		t.Fatalf("both current: got %d, err %v", pending, err)
	}
	if pending, complete, err := repo.ComponentStatus(ctx, "both", required); err != nil || pending != 0 || !complete {
		t.Fatalf("combined current status: pending=%d complete=%v err=%v", pending, complete, err)
	}
	var mask int
	if err := db.Get(&mask, `SELECT analysis_pending_mask FROM tracks WHERE id = 'both'`); err != nil || mask != 0 {
		t.Fatalf("both pending mask: got %d, err %v", mask, err)
	}
	if err := repo.UpsertComponentFeatures(ctx, &domain.TrackFeatures{TrackID: "aubio-only", AnalyzedAt: time.Now().UTC(), Tempo: 90}, domain.AnalysisComponentAubio, required); err != nil {
		t.Fatal(err)
	}
	if pending, err := repo.PendingComponents(ctx, "aubio-only", required); err != nil || pending != domain.AnalysisComponentFFmpeg {
		t.Fatalf("expected only ffmpeg pending: got %d, err %v", pending, err)
	}
	if err := db.Get(&mask, `SELECT analysis_pending_mask FROM tracks WHERE id = 'aubio-only'`); err != nil || mask != int(domain.AnalysisComponentFFmpeg) {
		t.Fatalf("aubio-only pending mask: got %d, err %v", mask, err)
	}
	ffmpegBumped := map[domain.AnalysisComponents]int{domain.AnalysisComponentFFmpeg: 2, domain.AnalysisComponentAubio: 1}
	// A source-version bump is accompanied by a migration that sets its bit in
	// the materialized pending mask; this keeps list/count queries indexable.
	if _, err := db.Exec(`UPDATE tracks SET analysis_pending_mask = analysis_pending_mask | 1 WHERE id = 'both'`); err != nil {
		t.Fatal(err)
	}
	if pending, err := repo.PendingComponents(ctx, "both", ffmpegBumped); err != nil || pending != domain.AnalysisComponentFFmpeg {
		t.Fatalf("ffmpeg bump should not stale aubio: got %d, err %v", pending, err)
	}
	if err := repo.MarkComponentsFailed(ctx, "aubio-only", domain.AnalysisComponentFFmpeg, required); err != nil {
		t.Fatal(err)
	}
	if pending, err := repo.PendingComponents(ctx, "aubio-only", required); err != nil || pending != 0 {
		t.Fatalf("failed component should be resolved at same version: got %d, err %v", pending, err)
	}
	complete, err := repo.ComponentsComplete(ctx, "aubio-only", required)
	if err != nil || complete {
		t.Fatalf("failed component must not be complete: complete=%v err=%v", complete, err)
	}
	if pending, complete, err := repo.ComponentStatus(ctx, "aubio-only", required); err != nil || pending != 0 || complete {
		t.Fatalf("combined failed status: pending=%d complete=%v err=%v", pending, complete, err)
	}
}
