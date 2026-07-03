package mood

import (
	"context"
	"fmt"
	"sort"
	"time"

	"airmedy/internal/domain"
)

// RecomputeCorpusPercentiles pulls every analyzed track's raw feature values
// out of track_features via repo, computes p1/p5/p50/p95/p99 per feature by
// sorting and linear-interpolating between ranks, and upserts the result
// through repo. Returns the freshly computed set (so callers can hot-swap an
// in-memory cache without a second read) and the sample count used. An empty
// corpus (sampleCount==0) is a no-op, not an error.
func RecomputeCorpusPercentiles(ctx context.Context, repo domain.AnalysisRepository) (PercentileSet, int, error) {
	values, err := repo.ListRawFeatureValues(ctx)
	if err != nil {
		return nil, 0, fmt.Errorf("mood: failed to list raw feature values: %w", err)
	}

	result := make(PercentileSet, len(AllFeatureNames))
	sampleCount := 0
	now := time.Now().UTC()
	rows := make([]domain.FeaturePercentileRow, 0, len(AllFeatureNames))

	for _, name := range AllFeatureNames {
		col := values[name]
		if len(col) == 0 {
			continue // no analyzed tracks yet; leave unset
		}
		sort.Float64s(col)
		p := Percentile{
			P1:  interpolatedPercentile(col, 0.01),
			P5:  interpolatedPercentile(col, 0.05),
			P50: interpolatedPercentile(col, 0.50),
			P95: interpolatedPercentile(col, 0.95),
			P99: interpolatedPercentile(col, 0.99),
		}
		result[name] = p
		sampleCount = len(col) // identical across features (one row per analyzed track)
		rows = append(rows, domain.FeaturePercentileRow{
			FeatureName: name,
			P1:          p.P1, P5: p.P5, P50: p.P50, P95: p.P95, P99: p.P99,
			SampleCount: len(col),
			ComputedAt:  now,
		})
	}

	if len(rows) == 0 {
		return result, 0, nil
	}
	if err := repo.UpsertFeaturePercentiles(ctx, rows); err != nil {
		return nil, 0, fmt.Errorf("mood: failed to upsert feature percentiles: %w", err)
	}
	return result, sampleCount, nil
}

// interpolatedPercentile returns the p-th percentile (p in [0,1]) of a
// pre-sorted slice using linear interpolation between closest ranks (the
// same method as numpy's default "linear" interpolation).
func interpolatedPercentile(sorted []float64, p float64) float64 {
	n := len(sorted)
	if n == 1 {
		return sorted[0]
	}
	rank := p * float64(n-1)
	lo := int(rank)
	hi := lo + 1
	if hi >= n {
		return sorted[n-1]
	}
	frac := rank - float64(lo)
	return sorted[lo] + frac*(sorted[hi]-sorted[lo])
}
