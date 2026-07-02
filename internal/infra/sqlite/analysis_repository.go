package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"airmedy/internal/domain"
)

type analysisRepository struct {
	db *DB
}

func NewAnalysisRepository(db *DB) domain.AnalysisRepository {
	return &analysisRepository{db: db}
}

func (r *analysisRepository) UpsertFeatures(ctx context.Context, f *domain.TrackFeatures) error {
	return r.db.RunTx(ctx, func(ctx context.Context) error {
		ex := r.db.Ext(ctx)

		// Phase-2 columns (loudness/dynamics/spectral) plus tempo (BPM, via aubio).
		// The remaining reserved Phase-6 mood columns are left untouched on conflict.
		_, err := ex.ExecContext(ctx,
			`INSERT INTO track_features (
			   track_id, analyzer_version, analyzed_at,
			   loudness_lufs, loudness_range, true_peak, rms, crest,
			   spectral_centroid, spectral_rolloff, spectral_flatness, spectral_flux, zcr,
			   tempo)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			 ON CONFLICT(track_id) DO UPDATE SET
			   analyzer_version = excluded.analyzer_version,
			   analyzed_at = excluded.analyzed_at,
			   loudness_lufs = excluded.loudness_lufs,
			   loudness_range = excluded.loudness_range,
			   true_peak = excluded.true_peak,
			   rms = excluded.rms,
			   crest = excluded.crest,
			   spectral_centroid = excluded.spectral_centroid,
			   spectral_rolloff = excluded.spectral_rolloff,
			   spectral_flatness = excluded.spectral_flatness,
			   spectral_flux = excluded.spectral_flux,
			   zcr = excluded.zcr,
			   tempo = excluded.tempo`,
			f.TrackID, f.AnalyzerVersion, f.AnalyzedAt,
			f.LoudnessLUFS, f.LoudnessRange, f.TruePeak, f.RMS, f.Crest,
			f.SpectralCentroid, f.SpectralRolloff, f.SpectralFlatness, f.SpectralFlux, f.ZCR,
			f.Tempo,
		)
		if err != nil {
			return fmt.Errorf("failed to upsert track features: %w", err)
		}

		if _, err = ex.ExecContext(ctx,
			`UPDATE tracks SET analyzed_version = ? WHERE id = ?`,
			f.AnalyzerVersion, f.TrackID,
		); err != nil {
			return fmt.Errorf("failed to bump analyzed_version: %w", err)
		}

		return nil
	})
}

func (r *analysisRepository) MarkFailed(ctx context.Context, trackID string, currentVersion int) error {
	if _, err := r.db.Ext(ctx).ExecContext(ctx,
		`UPDATE tracks SET analyzed_version = ? WHERE id = ?`,
		currentVersion, trackID,
	); err != nil {
		return fmt.Errorf("failed to bump analyzed_version for failed track: %w", err)
	}
	return nil
}

func (r *analysisRepository) GetFeatures(ctx context.Context, trackID string) (*domain.TrackFeatures, error) {
	var row struct {
		TrackID          string       `db:"track_id"`
		AnalyzerVersion  int          `db:"analyzer_version"`
		AnalyzedAt       sql.NullTime `db:"analyzed_at"`
		LoudnessLUFS     float64      `db:"loudness_lufs"`
		LoudnessRange    float64      `db:"loudness_range"`
		TruePeak         float64      `db:"true_peak"`
		RMS              float64      `db:"rms"`
		Crest            float64      `db:"crest"`
		SpectralCentroid float64      `db:"spectral_centroid"`
		SpectralRolloff  float64      `db:"spectral_rolloff"`
		SpectralFlatness float64      `db:"spectral_flatness"`
		SpectralFlux     float64      `db:"spectral_flux"`
		ZCR              float64      `db:"zcr"`
		Tempo            float64      `db:"tempo"`
		MusicalKey       string       `db:"musical_key"`
		Mode             string       `db:"mode"`
		Valence          float64      `db:"valence"`
		Energy           float64      `db:"energy"`
		Danceability     float64      `db:"danceability"`
	}
	err := r.db.Ext(ctx).GetContext(ctx, &row,
		`SELECT
		   track_id, analyzer_version, analyzed_at,
		   COALESCE(loudness_lufs, 0) AS loudness_lufs,
		   COALESCE(loudness_range, 0) AS loudness_range,
		   COALESCE(true_peak, 0) AS true_peak,
		   COALESCE(rms, 0) AS rms,
		   COALESCE(crest, 0) AS crest,
		   COALESCE(spectral_centroid, 0) AS spectral_centroid,
		   COALESCE(spectral_rolloff, 0) AS spectral_rolloff,
		   COALESCE(spectral_flatness, 0) AS spectral_flatness,
		   COALESCE(spectral_flux, 0) AS spectral_flux,
		   COALESCE(zcr, 0) AS zcr,
		   COALESCE(tempo, 0) AS tempo,
		   COALESCE(musical_key, '') AS musical_key,
		   COALESCE(mode, '') AS mode,
		   COALESCE(valence, 0) AS valence,
		   COALESCE(energy, 0) AS energy,
		   COALESCE(danceability, 0) AS danceability
		 FROM track_features WHERE track_id = ?`,
		trackID,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get track features: %w", err)
	}

	analyzedAt := time.Time{}
	if row.AnalyzedAt.Valid {
		analyzedAt = row.AnalyzedAt.Time
	}
	return &domain.TrackFeatures{
		TrackID:          row.TrackID,
		AnalyzerVersion:  row.AnalyzerVersion,
		AnalyzedAt:       analyzedAt,
		LoudnessLUFS:     row.LoudnessLUFS,
		LoudnessRange:    row.LoudnessRange,
		TruePeak:         row.TruePeak,
		RMS:              row.RMS,
		Crest:            row.Crest,
		SpectralCentroid: row.SpectralCentroid,
		SpectralRolloff:  row.SpectralRolloff,
		SpectralFlatness: row.SpectralFlatness,
		SpectralFlux:     row.SpectralFlux,
		ZCR:              row.ZCR,
		Tempo:            row.Tempo,
		MusicalKey:       row.MusicalKey,
		Mode:             row.Mode,
		Valence:          row.Valence,
		Energy:           row.Energy,
		Danceability:     row.Danceability,
	}, nil
}

func (r *analysisRepository) IsAnalyzed(ctx context.Context, trackID string, currentVersion int) (bool, error) {
	var version int
	err := r.db.Ext(ctx).GetContext(ctx, &version,
		`SELECT analyzed_version FROM tracks WHERE id = ?`, trackID,
	)
	if err == sql.ErrNoRows {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("failed to get track analyzed_version: %w", err)
	}
	return version >= currentVersion, nil
}

func (r *analysisRepository) CountPending(ctx context.Context, currentVersion int) (int, error) {
	var n int
	if err := r.db.Ext(ctx).GetContext(ctx, &n,
		`SELECT COUNT(*) FROM tracks WHERE analyzed_version < ?`, currentVersion,
	); err != nil {
		return 0, fmt.Errorf("failed to count pending analysis: %w", err)
	}
	return n, nil
}

func (r *analysisRepository) ListPending(ctx context.Context, currentVersion, limit int) ([]string, error) {
	var ids []string
	if err := r.db.Ext(ctx).SelectContext(ctx, &ids,
		`SELECT id FROM tracks WHERE analyzed_version < ? ORDER BY rowid ASC LIMIT ?`,
		currentVersion, limit,
	); err != nil {
		return nil, fmt.Errorf("failed to list pending analysis: %w", err)
	}
	return ids, nil
}
