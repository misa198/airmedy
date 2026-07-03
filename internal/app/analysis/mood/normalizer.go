// Package mood derives rule-based mood/rhythm scores (energy, danceability)
// from a track's raw DSP features, normalized against corpus-wide
// percentiles. Pure Go, no cgo/db dependency except percentiles.go's use of
// domain.AnalysisRepository to pull/persist the corpus stats.
package mood

import "math"

// sigmoidSteepness (k) controls how sharply Normalize saturates toward 0/1
// away from the corpus median. Locked formula range is 2.0-3.0.
const sigmoidSteepness = 2.5

// Percentile holds the cached corpus distribution for one raw feature.
type Percentile struct {
	P1, P5, P50, P95, P99 float64
}

// Normalize maps a raw feature value x to [0,1] via two-stage clamp+sigmoid:
// hard-clamp to [P1,P99], then sigmoid(k * z) where z is x's distance from
// the median in half-IQR-ish (P95-P5)/2 units.
//
// If pctl.P95 == pctl.P5 (degenerate/empty corpus, e.g. before any
// percentiles have been computed, or a feature constant across the
// library), the soft-clamp denominator is 0; Normalize returns 0.5
// (neutral) instead of dividing by zero.
func Normalize(x float64, pctl Percentile, k float64) float64 {
	clamped := clamp(x, pctl.P1, pctl.P99)
	spread := (pctl.P95 - pctl.P5) / 2
	if spread == 0 {
		return 0.5
	}
	z := (clamped - pctl.P50) / spread
	return sigmoid(k * z)
}

func clamp(x, lo, hi float64) float64 {
	if lo > hi {
		lo, hi = hi, lo
	}
	if x < lo {
		return lo
	}
	if x > hi {
		return hi
	}
	return x
}

func sigmoid(x float64) float64 {
	return 1 / (1 + math.Exp(-x))
}
