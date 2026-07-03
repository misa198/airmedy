package sqlite

import (
	"context"
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
	similarityWeightTempo        = 1.0
)

func (r *trackQueryRepository) FindSimilar(ctx context.Context, seedTrackID string, limit int, decayFactor float64) ([]*domain.TrackDTO, error) {
	// Distance is computed purely in SQL against the seed's own feature row
	// (correlated subqueries), so we never round-trip seed values through
	// Go. Tempo is on a very different numeric scale (BPM, ~40-220) than
	// energy/danceability (0-1 normalized), so it's divided by 200 before
	// squaring to bring it into a comparable range — otherwise it would
	// dominate the distance regardless of weighting. Unanalyzed tracks
	// (NULL features) are excluded so they never rank as spuriously "close"
	// due to SQL NULL arithmetic.
	query := `
		SELECT t.id
		FROM tracks t
		JOIN track_features tf ON tf.track_id = t.id
		WHERE t.id != ?
		  AND tf.energy IS NOT NULL
		  AND tf.danceability IS NOT NULL
		  AND tf.tempo IS NOT NULL
		ORDER BY (
			? * (tf.energy       - (SELECT energy       FROM track_features WHERE track_id = ?)) *
			     (tf.energy       - (SELECT energy       FROM track_features WHERE track_id = ?)) +
			? * (tf.danceability - (SELECT danceability FROM track_features WHERE track_id = ?)) *
			     (tf.danceability - (SELECT danceability FROM track_features WHERE track_id = ?)) +
			? * ((tf.tempo - (SELECT tempo FROM track_features WHERE track_id = ?)) / 200.0) *
			     ((tf.tempo - (SELECT tempo FROM track_features WHERE track_id = ?)) / 200.0)
		) ASC
		LIMIT ?
	`
	args := []any{
		seedTrackID,
		similarityWeightEnergy, seedTrackID, seedTrackID,
		similarityWeightDanceability, seedTrackID, seedTrackID,
		similarityWeightTempo, seedTrackID, seedTrackID,
		limit,
	}

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
