package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
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

		// Phase-2 columns (loudness/dynamics/spectral) plus tempo (BPM, via aubio)
		// and onset_variance. The remaining reserved mood columns
		// (energy/danceability/brightness) are left untouched on conflict — written
		// separately by the mood-derivation stage.
		_, err := ex.ExecContext(ctx,
			`INSERT INTO track_features (
			   track_id, analyzer_version, analyzed_at,
			   loudness_lufs, loudness_range, true_peak, rms, crest,
			   spectral_centroid, spectral_rolloff, spectral_flatness, spectral_flux, zcr,
			   tempo, onset_variance)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
			   tempo = excluded.tempo,
			   onset_variance = excluded.onset_variance`,
			f.TrackID, f.AnalyzerVersion, f.AnalyzedAt,
			f.LoudnessLUFS, f.LoudnessRange, f.TruePeak, f.RMS, f.Crest,
			f.SpectralCentroid, f.SpectralRolloff, f.SpectralFlatness, f.SpectralFlux, f.ZCR,
			f.Tempo, f.OnsetVariance,
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
		OnsetVariance    float64      `db:"onset_variance"`
		Energy           float64      `db:"energy"`
		Danceability     float64      `db:"danceability"`
		Brightness       float64      `db:"brightness"`
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
		   COALESCE(onset_variance, 0) AS onset_variance,
		   COALESCE(energy, 0) AS energy,
		   COALESCE(danceability, 0) AS danceability,
		   COALESCE(brightness, 0) AS brightness
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
		OnsetVariance:    row.OnsetVariance,
		Energy:           row.Energy,
		Danceability:     row.Danceability,
		Brightness:       row.Brightness,
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

func (r *analysisRepository) CountAll(ctx context.Context) (int, error) {
	var n int
	if err := r.db.Ext(ctx).GetContext(ctx, &n,
		`SELECT COUNT(*) FROM tracks`,
	); err != nil {
		return 0, fmt.Errorf("failed to count all tracks: %w", err)
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

func componentName(component domain.AnalysisComponents) (string, error) {
	switch component {
	case domain.AnalysisComponentFFmpeg:
		return "ffmpeg", nil
	case domain.AnalysisComponentAubio:
		return "aubio", nil
	default:
		return "", fmt.Errorf("unknown analysis component %d", component)
	}
}

func requestedComponents(required map[domain.AnalysisComponents]int) []domain.AnalysisComponents {
	components := make([]domain.AnalysisComponents, 0, 2)
	for _, component := range []domain.AnalysisComponents{domain.AnalysisComponentFFmpeg, domain.AnalysisComponentAubio} {
		if required[component] > 0 {
			components = append(components, component)
		}
	}
	return components
}

func (r *analysisRepository) PendingComponents(ctx context.Context, trackID string, required map[domain.AnalysisComponents]int) (domain.AnalysisComponents, error) {
	var mask int
	err := r.db.Ext(ctx).GetContext(ctx, &mask, `SELECT analysis_pending_mask FROM tracks WHERE id = ?`, trackID)
	if err == sql.ErrNoRows {
		return domain.AnalysisComponentsAll, nil
	}
	if err != nil {
		return 0, fmt.Errorf("failed to get pending analysis mask: %w", err)
	}
	return domain.AnalysisComponents(mask) & domain.AnalysisComponentsAll, nil
}

func (r *analysisRepository) ComponentsComplete(ctx context.Context, trackID string, required map[domain.AnalysisComponents]int) (bool, error) {
	for _, component := range requestedComponents(required) {
		name, _ := componentName(component)
		var status string
		err := r.db.Ext(ctx).GetContext(ctx, &status, `SELECT status FROM track_analysis_components WHERE track_id = ? AND component = ? AND version >= ?`, trackID, name, required[component])
		if err == sql.ErrNoRows {
			return false, nil
		}
		if err != nil {
			return false, fmt.Errorf("failed to get %s analysis status: %w", name, err)
		}
		if status != "complete" {
			return false, nil
		}
	}
	return true, nil
}

func (r *analysisRepository) ComponentStatus(ctx context.Context, trackID string, required map[domain.AnalysisComponents]int) (domain.AnalysisComponents, bool, error) {
	components := requestedComponents(required)
	if len(components) == 0 {
		return 0, true, nil
	}
	names := make([]any, 0, len(components)+1)
	names = append(names, trackID)
	placeholders := make([]string, 0, len(components))
	for _, component := range components {
		name, _ := componentName(component)
		placeholders = append(placeholders, "?")
		names = append(names, name)
	}
	type statusRow struct {
		Component string `db:"component"`
		Version   int    `db:"version"`
		Status    string `db:"status"`
	}
	type componentStatusRow struct {
		PendingMask int `db:"analysis_pending_mask"`
		statusRow
	}
	var rows []componentStatusRow
	query := `SELECT t.analysis_pending_mask, COALESCE(c.component, '') AS component, COALESCE(c.version, 0) AS version, COALESCE(c.status, '') AS status FROM tracks t LEFT JOIN track_analysis_components c ON c.track_id = t.id AND c.component IN (` + strings.Join(placeholders, ",") + `) WHERE t.id = ?`
	args := append(names[1:], names[0])
	if err := r.db.Ext(ctx).SelectContext(ctx, &rows, query, args...); err != nil {
		return 0, false, fmt.Errorf("failed to get component analysis status: %w", err)
	}
	if len(rows) == 0 {
		return domain.AnalysisComponentsAll, false, nil
	}
	stored := make(map[string]statusRow, len(components))
	for _, row := range rows {
		if row.Component != "" {
			stored[row.Component] = row.statusRow
		}
	}
	pending := domain.AnalysisComponents(rows[0].PendingMask) & domain.AnalysisComponentsAll
	complete := pending == 0
	for _, component := range components {
		name, _ := componentName(component)
		row, ok := stored[name]
		if !ok || row.Version < required[component] || pending&component != 0 {
			complete = false
			continue
		}
		if row.Status != "complete" {
			complete = false
		}
	}
	return pending, complete, nil
}

func (r *analysisRepository) ListPendingComponentTracks(ctx context.Context, required map[domain.AnalysisComponents]int, limit int) ([]string, error) {
	var ids []string
	if err := r.db.Ext(ctx).SelectContext(ctx, &ids, `SELECT id FROM tracks WHERE analysis_pending_mask != 0 ORDER BY created_at ASC, id ASC LIMIT ?`, limit); err != nil {
		return nil, fmt.Errorf("failed to list pending component analysis: %w", err)
	}
	return ids, nil
}

func (r *analysisRepository) CountPendingComponentTracks(ctx context.Context, required map[domain.AnalysisComponents]int) (int, error) {
	var count int
	if err := r.db.Ext(ctx).GetContext(ctx, &count, `SELECT COUNT(*) FROM tracks WHERE analysis_pending_mask != 0`); err != nil {
		return 0, fmt.Errorf("failed to count pending component analysis: %w", err)
	}
	return count, nil
}

func (r *analysisRepository) CountFailedComponentTracks(ctx context.Context, required map[domain.AnalysisComponents]int) (int, error) {
	var count int
	if err := r.db.Ext(ctx).GetContext(ctx, &count, `SELECT COUNT(DISTINCT track_id) FROM track_analysis_components WHERE status = 'failed'`); err != nil {
		return 0, fmt.Errorf("failed to count failed component analysis: %w", err)
	}
	return count, nil
}

func (r *analysisRepository) ListFailedComponentTracks(ctx context.Context, required map[domain.AnalysisComponents]int) ([]domain.FailedAnalysisTrack, error) {
	type row struct {
		domain.FailedAnalysisTrack
		Components string `db:"components"`
	}
	var rows []row
	if err := r.db.Ext(ctx).SelectContext(ctx, &rows, `SELECT t.id, t.title, COALESCE(GROUP_CONCAT(DISTINCT a.name), '') AS artists, t.path, t.artwork_key AS artwork_key, GROUP_CONCAT(DISTINCT c.component) AS components FROM tracks t JOIN track_analysis_components c ON c.track_id = t.id AND c.status = 'failed' LEFT JOIN track_artists ta ON ta.track_id = t.id LEFT JOIN artists a ON a.id = ta.artist_id GROUP BY t.id ORDER BY t.title, t.id`); err != nil {
		return nil, fmt.Errorf("failed to list failed component analysis: %w", err)
	}
	out := make([]domain.FailedAnalysisTrack, len(rows))
	for i, row := range rows {
		out[i] = row.FailedAnalysisTrack
		out[i].FailedComponents = strings.Split(row.Components, ",")
	}
	return out, nil
}

func (r *analysisRepository) ResetFailedComponents(ctx context.Context, required map[domain.AnalysisComponents]int) ([]string, error) {
	var ids []string
	err := r.db.RunTx(ctx, func(ctx context.Context) error {
		ex := r.db.Ext(ctx)
		if err := ex.SelectContext(ctx, &ids, `SELECT DISTINCT track_id FROM track_analysis_components WHERE status = 'failed'`); err != nil {
			return fmt.Errorf("failed to list failed component analysis for retry: %w", err)
		}
		if len(ids) == 0 {
			return nil
		}
		if _, err := ex.ExecContext(ctx, `UPDATE tracks SET analysis_pending_mask = analysis_pending_mask | 1 WHERE id IN (SELECT track_id FROM track_analysis_components WHERE status = 'failed' AND component = 'ffmpeg')`); err != nil {
			return fmt.Errorf("failed to restore pending analysis components: %w", err)
		}
		if _, err := ex.ExecContext(ctx, `UPDATE tracks SET analysis_pending_mask = analysis_pending_mask | 2 WHERE id IN (SELECT track_id FROM track_analysis_components WHERE status = 'failed' AND component = 'aubio')`); err != nil {
			return fmt.Errorf("failed to restore pending analysis components: %w", err)
		}
		if _, err := ex.ExecContext(ctx, `DELETE FROM track_analysis_components WHERE status = 'failed'`); err != nil {
			return fmt.Errorf("failed to clear failed component analysis for retry: %w", err)
		}
		return nil
	})
	return ids, err
}

func (r *analysisRepository) UpsertComponentFeatures(ctx context.Context, f *domain.TrackFeatures, components domain.AnalysisComponents, versions map[domain.AnalysisComponents]int) error {
	return r.db.RunTx(ctx, func(ctx context.Context) error {
		ex := r.db.Ext(ctx)
		if _, err := ex.ExecContext(ctx, `INSERT INTO track_features (track_id, analyzer_version, analyzed_at) VALUES (?, 0, ?) ON CONFLICT(track_id) DO NOTHING`, f.TrackID, f.AnalyzedAt); err != nil {
			return fmt.Errorf("failed to create track features: %w", err)
		}
		if components&domain.AnalysisComponentFFmpeg != 0 {
			if _, err := ex.ExecContext(ctx, `UPDATE track_features SET analyzer_version = 4, analyzed_at = ?, loudness_lufs = ?, loudness_range = ?, true_peak = ?, rms = ?, crest = ?, spectral_centroid = ?, spectral_rolloff = ?, spectral_flatness = ?, spectral_flux = ?, zcr = ? WHERE track_id = ?`, f.AnalyzedAt, f.LoudnessLUFS, f.LoudnessRange, f.TruePeak, f.RMS, f.Crest, f.SpectralCentroid, f.SpectralRolloff, f.SpectralFlatness, f.SpectralFlux, f.ZCR, f.TrackID); err != nil {
				return fmt.Errorf("failed to update ffmpeg features: %w", err)
			}
		}
		if components&domain.AnalysisComponentAubio != 0 {
			if _, err := ex.ExecContext(ctx, `UPDATE track_features SET analyzed_at = ?, tempo = ?, onset_variance = ? WHERE track_id = ?`, f.AnalyzedAt, f.Tempo, f.OnsetVariance, f.TrackID); err != nil {
				return fmt.Errorf("failed to update aubio features: %w", err)
			}
		}
		for _, component := range requestedComponents(versions) {
			if components&component == 0 {
				continue
			}
			name, _ := componentName(component)
			if _, err := ex.ExecContext(ctx, `INSERT INTO track_analysis_components (track_id, component, version, status, analyzed_at) VALUES (?, ?, ?, 'complete', ?) ON CONFLICT(track_id, component) DO UPDATE SET version = excluded.version, status = excluded.status, analyzed_at = excluded.analyzed_at`, f.TrackID, name, versions[component], f.AnalyzedAt); err != nil {
				return fmt.Errorf("failed to mark %s analysis complete: %w", name, err)
			}
		}
		remainingMask := int(domain.AnalysisComponentsAll &^ components)
		if _, err := ex.ExecContext(ctx, `UPDATE tracks SET analyzed_version = 4, analysis_pending_mask = analysis_pending_mask & ? WHERE id = ?`, remainingMask, f.TrackID); err != nil {
			return fmt.Errorf("failed to maintain legacy analyzed version: %w", err)
		}
		return nil
	})
}

func (r *analysisRepository) MarkComponentsFailed(ctx context.Context, trackID string, components domain.AnalysisComponents, versions map[domain.AnalysisComponents]int) error {
	for _, component := range requestedComponents(versions) {
		if components&component == 0 {
			continue
		}
		name, _ := componentName(component)
		if _, err := r.db.Ext(ctx).ExecContext(ctx, `INSERT INTO track_analysis_components (track_id, component, version, status) VALUES (?, ?, ?, 'failed') ON CONFLICT(track_id, component) DO UPDATE SET version = excluded.version, status = excluded.status, analyzed_at = NULL`, trackID, name, versions[component]); err != nil {
			return fmt.Errorf("failed to mark %s analysis failed: %w", name, err)
		}
	}
	remainingMask := int(domain.AnalysisComponentsAll &^ components)
	if _, err := r.db.Ext(ctx).ExecContext(ctx, `UPDATE tracks SET analyzed_version = 4, analysis_pending_mask = analysis_pending_mask & ? WHERE id = ?`, remainingMask, trackID); err != nil {
		return fmt.Errorf("failed to maintain legacy analyzed version for failed track: %w", err)
	}
	return nil
}

// UpsertMoodFeatures writes derived energy/danceability/brightness for a track and, in
// the same transaction, bumps tracks.mood_derived_version. A plain UPDATE
// (not upsert): a track_features row always exists by the time mood
// derivation runs, since it's always downstream of a successful UpsertFeatures.
func (r *analysisRepository) UpsertMoodFeatures(ctx context.Context, trackID string, energy, danceability, brightness float64, moodVersion int) error {
	return r.db.RunTx(ctx, func(ctx context.Context) error {
		ex := r.db.Ext(ctx)

		res, err := ex.ExecContext(ctx,
			`UPDATE track_features SET energy = ?, danceability = ?, brightness = ? WHERE track_id = ?`,
			energy, danceability, brightness, trackID,
		)
		if err != nil {
			return fmt.Errorf("failed to upsert mood features: %w", err)
		}
		if n, _ := res.RowsAffected(); n == 0 {
			return fmt.Errorf("failed to upsert mood features: no track_features row for track %s", trackID)
		}

		if _, err = ex.ExecContext(ctx,
			`UPDATE tracks SET mood_derived_version = ? WHERE id = ?`,
			moodVersion, trackID,
		); err != nil {
			return fmt.Errorf("failed to bump mood_derived_version: %w", err)
		}
		return nil
	})
}

// GetFeaturePercentiles returns the full cached corpus percentile table.
func (r *analysisRepository) GetFeaturePercentiles(ctx context.Context) (map[string]domain.FeaturePercentileRow, error) {
	var rows []domain.FeaturePercentileRow
	if err := r.db.Ext(ctx).SelectContext(ctx, &rows,
		`SELECT feature_name, p1, p5, p50, p95, p99, sample_count, computed_at FROM feature_percentiles`,
	); err != nil {
		return nil, fmt.Errorf("failed to load feature percentiles: %w", err)
	}
	out := make(map[string]domain.FeaturePercentileRow, len(rows))
	for _, row := range rows {
		out[row.FeatureName] = row
	}
	return out, nil
}

// UpsertFeaturePercentiles replaces the stored percentile rows for the given features.
func (r *analysisRepository) UpsertFeaturePercentiles(ctx context.Context, rows []domain.FeaturePercentileRow) error {
	return r.db.RunTx(ctx, func(ctx context.Context) error {
		ex := r.db.Ext(ctx)
		for _, row := range rows {
			if _, err := ex.ExecContext(ctx,
				`INSERT INTO feature_percentiles (feature_name, p1, p5, p50, p95, p99, sample_count, computed_at)
				 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				 ON CONFLICT(feature_name) DO UPDATE SET
				   p1 = excluded.p1, p5 = excluded.p5, p50 = excluded.p50,
				   p95 = excluded.p95, p99 = excluded.p99,
				   sample_count = excluded.sample_count, computed_at = excluded.computed_at`,
				row.FeatureName, row.P1, row.P5, row.P50, row.P95, row.P99, row.SampleCount, row.ComputedAt,
			); err != nil {
				return fmt.Errorf("failed to upsert feature percentile %q: %w", row.FeatureName, err)
			}
		}
		return nil
	})
}

// moodFeatureColumns lists the track_features columns that feed corpus
// percentile computation. Column names are identical to the feature names
// used elsewhere in the mood package (mood.AllFeatureNames) — this slice is
// the SQL-facing counterpart, kept as a plain literal since ListRawFeatureValues
// only ever interpolates from this fixed list, never user input.
var moodFeatureColumns = []string{
	"rms", "spectral_centroid", "spectral_flux",
	"tempo", "crest", "onset_variance", "loudness_range",
}

// ListRawFeatureValues returns, per feature, every analyzed track's raw
// value for that feature. One SELECT per feature keeps this simple;
// corpus recompute runs infrequently (every N tracks / daily), not a hot path.
// Rows where a given column is NULL (e.g. onset_variance on tracks aubio
// couldn't detect a stable beat on) are excluded from that column only —
// a NULL in one feature must not fail percentile computation for the rest.
func (r *analysisRepository) ListRawFeatureValues(ctx context.Context) (map[string][]float64, error) {
	out := make(map[string][]float64, len(moodFeatureColumns))
	for _, column := range moodFeatureColumns {
		var vals []float64
		query := fmt.Sprintf(`SELECT %s FROM track_features WHERE analyzer_version > 0 AND %s IS NOT NULL`, column, column)
		if err := r.db.Ext(ctx).SelectContext(ctx, &vals, query); err != nil {
			return nil, fmt.Errorf("failed to list raw values for %q: %w", column, err)
		}
		out[column] = vals
	}
	return out, nil
}

// ListMoodPending returns track IDs with raw features already present
// (track_features row exists) whose mood_derived_version is stale.
func (r *analysisRepository) ListMoodPending(ctx context.Context, currentMoodVersion, limit int) ([]string, error) {
	var ids []string
	if err := r.db.Ext(ctx).SelectContext(ctx, &ids,
		`SELECT t.id FROM tracks t
		 JOIN track_features tf ON tf.track_id = t.id
		 WHERE t.mood_derived_version < ?
		 ORDER BY t.rowid ASC LIMIT ?`,
		currentMoodVersion, limit,
	); err != nil {
		return nil, fmt.Errorf("failed to list pending mood derivation: %w", err)
	}
	return ids, nil
}
