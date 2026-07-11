package sqlite

import (
	"context"
	"encoding/json"
	"fmt"

	"airmedy/internal/domain"
)

type trackQueryRepository struct {
	db     *DB
	tracks domain.TrackRepository // delegated to for hydration (GetByIDs)
}

func NewTrackQueryRepository(db *DB, tracks domain.TrackRepository) domain.TrackQueryRepository {
	return &trackQueryRepository{db: db, tracks: tracks}
}

// Similarity weights for FindSimilar's weighted-euclidean distance. Equal
// weighting for now; tune independently later without touching query shape.
const (
	similarityWeightEnergy       = 1.0
	similarityWeightDanceability = 1.0
	similarityWeightBrightness   = 1.0
	similarityWeightTempo        = 1.0
)

func (r *trackQueryRepository) FindSimilar(ctx context.Context, seedTrackID string, excludeTrackIDs []string, limit int) ([]*domain.TrackDTO, error) {
	// If the seed track itself has no analyzed feature row, every correlated
	// subquery below yields NULL, so all distances are NULL and ORDER BY
	// degenerates to an arbitrary order — returning tracks unrelated to the
	// seed. Guard against it: no seed features means no meaningful similarity.
	var seedFeatures int
	if err := r.db.Ext(ctx).GetContext(ctx, &seedFeatures,
		`SELECT COUNT(*) FROM track_features
		 WHERE track_id = ?
		   AND energy IS NOT NULL
		   AND danceability IS NOT NULL
		   AND brightness IS NOT NULL
		   AND tempo IS NOT NULL`, seedTrackID); err != nil {
		return nil, fmt.Errorf("failed to check seed track features: %w", err)
	}
	if seedFeatures == 0 {
		return nil, nil
	}

	// Distance is computed purely in SQL against the seed's own feature row
	// (correlated subqueries), so we never round-trip seed values through
	// Go. Tempo is on a very different numeric scale (BPM, ~40-220) than
	// energy/danceability/brightness (0-1 normalized), so tempo is divided by 200 before
	// squaring to bring it into a comparable range — otherwise it would
	// dominate the distance regardless of weighting. Unanalyzed tracks
	// (NULL features) are excluded so they never rank as spuriously "close"
	// due to SQL NULL arithmetic.
	exclusions := ""
	var exclusionJSON string
	if len(excludeTrackIDs) > 0 {
		exclusionBytes, err := json.Marshal(excludeTrackIDs)
		if err != nil {
			return nil, fmt.Errorf("failed to encode excluded track IDs: %w", err)
		}
		exclusionJSON = string(exclusionBytes)
		// json_each keeps the entire exclusion list in one bind parameter. This
		// avoids SQLite's variable limit when a user's queue has thousands of
		// tracks, without needing a connection-scoped temporary table.
		exclusions = `
		  AND NOT EXISTS (
			  SELECT 1 FROM json_each(?) excluded
			  WHERE excluded.value = t.id
		  )`
	}
	query := `
		SELECT t.id
		FROM tracks t
		JOIN track_features tf ON tf.track_id = t.id
		WHERE t.id != ?
		` + exclusions + `
		  AND tf.energy IS NOT NULL
		  AND tf.danceability IS NOT NULL
		  AND tf.brightness IS NOT NULL
		  AND tf.tempo IS NOT NULL
		ORDER BY (
			? * (tf.energy       - (SELECT energy       FROM track_features WHERE track_id = ?)) *
			     (tf.energy       - (SELECT energy       FROM track_features WHERE track_id = ?)) +
			? * (tf.danceability - (SELECT danceability FROM track_features WHERE track_id = ?)) *
			     (tf.danceability - (SELECT danceability FROM track_features WHERE track_id = ?)) +
			? * (tf.brightness   - (SELECT brightness   FROM track_features WHERE track_id = ?)) *
			     (tf.brightness   - (SELECT brightness   FROM track_features WHERE track_id = ?)) +
			? * ((tf.tempo - (SELECT tempo FROM track_features WHERE track_id = ?)) / 200.0) *
			     ((tf.tempo - (SELECT tempo FROM track_features WHERE track_id = ?)) / 200.0)
		) ASC
		LIMIT ?
	`
	args := []any{
		seedTrackID,
	}
	if exclusionJSON != "" {
		args = append(args, exclusionJSON)
	}
	args = append(args,
		similarityWeightEnergy, seedTrackID, seedTrackID,
		similarityWeightDanceability, seedTrackID, seedTrackID,
		similarityWeightBrightness, seedTrackID, seedTrackID,
		similarityWeightTempo, seedTrackID, seedTrackID,
		limit,
	)

	var ids []string
	if err := r.db.Ext(ctx).SelectContext(ctx, &ids, query, args...); err != nil {
		return nil, fmt.Errorf("failed to find similar tracks: %w", err)
	}
	if len(ids) == 0 {
		return nil, nil
	}

	hydrated, err := r.tracks.GetByIDs(ctx, ids)
	if err != nil {
		return nil, fmt.Errorf("failed to hydrate similar tracks: %w", err)
	}
	return reorderByIDs(hydrated, ids), nil
}

// MoodDensityGrid buckets every track with a non-null energy and danceability
// into a gridSize x gridSize grid over the fixed [0,1]x[0,1] space those
// features live in (sigmoid-normalized by mood.Normalize, so no need to query
// min/max from data). Bucketing happens in SQL; a value of exactly 1.0 is
// clamped into the last bucket (gridSize-1) rather than overflowing.
func (r *trackQueryRepository) MoodDensityGrid(ctx context.Context, gridSize int) (*domain.MoodDensityGrid, error) {
	type bucketRow struct {
		X int `db:"x_bucket"`
		Y int `db:"y_bucket"`
		N int `db:"n"`
	}
	var rows []bucketRow
	err := r.db.Ext(ctx).SelectContext(ctx, &rows, `
		SELECT
			MIN(CAST(danceability * ? AS INTEGER), ? - 1) AS x_bucket,
			MIN(CAST(energy       * ? AS INTEGER), ? - 1) AS y_bucket,
			COUNT(*) AS n
		FROM track_features
		WHERE energy IS NOT NULL AND danceability IS NOT NULL
		GROUP BY x_bucket, y_bucket
	`, gridSize, gridSize, gridSize, gridSize)
	if err != nil {
		return nil, fmt.Errorf("failed to bucket mood density grid: %w", err)
	}

	counts := make([][]int, gridSize)
	for x := range counts {
		counts[x] = make([]int, gridSize)
	}
	analyzedCount := 0
	for _, row := range rows {
		counts[row.X][row.Y] = row.N
		analyzedCount += row.N
	}

	var totalCount int
	if err := r.db.Ext(ctx).GetContext(ctx, &totalCount, `SELECT COUNT(*) FROM tracks`); err != nil {
		return nil, fmt.Errorf("failed to count tracks: %w", err)
	}

	return &domain.MoodDensityGrid{
		GridSize:      gridSize,
		Counts:        counts,
		AnalyzedCount: analyzedCount,
		TotalCount:    totalCount,
	}, nil
}

// reorderByIDs re-orders hydrated (as returned by TrackRepository.GetByIDs)
// to match ids' order. Tracks in ids that GetByIDs didn't return (e.g.
// deleted between the two queries) are silently dropped.
func reorderByIDs(hydrated []*domain.TrackDTO, ids []string) []*domain.TrackDTO {
	byID := make(map[string]*domain.TrackDTO, len(hydrated))
	for _, t := range hydrated {
		byID[t.ID] = t
	}
	ordered := make([]*domain.TrackDTO, 0, len(ids))
	for _, id := range ids {
		if t, ok := byID[id]; ok {
			ordered = append(ordered, t)
		}
	}
	return ordered
}
