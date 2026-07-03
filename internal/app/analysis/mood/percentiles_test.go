package mood

import (
	"context"
	"testing"

	"airmedy/internal/domain"
)

func TestInterpolatedPercentile_KnownValues(t *testing.T) {
	sorted := []float64{10, 20, 30, 40, 50}
	if got := interpolatedPercentile(sorted, 0.50); got != 30 {
		t.Errorf("p50 = %v, want 30", got)
	}
	if got := interpolatedPercentile(sorted, 0.25); got != 20 {
		t.Errorf("p25 = %v, want 20 (rank=1.0 exactly)", got)
	}
	if got := interpolatedPercentile(sorted, 0.0); got != 10 {
		t.Errorf("p0 = %v, want 10", got)
	}
	if got := interpolatedPercentile(sorted, 1.0); got != 50 {
		t.Errorf("p100 = %v, want 50", got)
	}
}

func TestInterpolatedPercentile_SingleElement(t *testing.T) {
	sorted := []float64{42}
	for _, p := range []float64{0.01, 0.5, 0.99} {
		if got := interpolatedPercentile(sorted, p); got != 42 {
			t.Errorf("interpolatedPercentile(single, %v) = %v, want 42", p, got)
		}
	}
}

func TestInterpolatedPercentile_TwoElements(t *testing.T) {
	sorted := []float64{0, 100}
	if got := interpolatedPercentile(sorted, 0.5); got != 50 {
		t.Errorf("p50 of [0,100] = %v, want 50", got)
	}
}

// fakeAnalysisRepo implements only the methods RecomputeCorpusPercentiles
// needs; embedding the interface lets the rest panic-on-call if accidentally
// exercised, same convention as the hand-written mocks in
// internal/app/analysis/analysis_service_test.go.
type fakeAnalysisRepo struct {
	domain.AnalysisRepository
	values        map[string][]float64
	upsertedRows  []domain.FeaturePercentileRow
	upsertCallCnt int
}

func (f *fakeAnalysisRepo) ListRawFeatureValues(ctx context.Context) (map[string][]float64, error) {
	return f.values, nil
}

func (f *fakeAnalysisRepo) UpsertFeaturePercentiles(ctx context.Context, rows []domain.FeaturePercentileRow) error {
	f.upsertedRows = rows
	f.upsertCallCnt++
	return nil
}

func TestRecomputeCorpusPercentiles_ComputesAndPersists(t *testing.T) {
	repo := &fakeAnalysisRepo{values: map[string][]float64{
		FeatureRMS:              {10, 20, 30, 40, 50},
		FeatureSpectralCentroid: {1, 2, 3, 4, 5},
		FeatureSpectralFlux:     {1, 2, 3, 4, 5},
		FeatureTempo:            {60, 90, 120, 150, 180},
		FeatureCrest:            {1, 2, 3, 4, 5},
		FeatureOnsetVariance:    {1, 2, 3, 4, 5},
		FeatureLoudnessRange:    {1, 2, 3, 4, 5},
	}}

	pctl, sampleCount, err := RecomputeCorpusPercentiles(context.Background(), repo)
	if err != nil {
		t.Fatalf("RecomputeCorpusPercentiles returned error: %v", err)
	}
	if sampleCount != 5 {
		t.Errorf("sampleCount = %d, want 5", sampleCount)
	}
	if pctl[FeatureRMS].P50 != 30 {
		t.Errorf("RMS p50 = %v, want 30", pctl[FeatureRMS].P50)
	}
	if pctl[FeatureTempo].P50 != 120 {
		t.Errorf("tempo p50 = %v, want 120", pctl[FeatureTempo].P50)
	}
	if repo.upsertCallCnt != 1 {
		t.Errorf("UpsertFeaturePercentiles call count = %d, want 1", repo.upsertCallCnt)
	}
	if len(repo.upsertedRows) != len(AllFeatureNames) {
		t.Errorf("upserted %d rows, want %d", len(repo.upsertedRows), len(AllFeatureNames))
	}
	for _, row := range repo.upsertedRows {
		if row.SampleCount != 5 {
			t.Errorf("row %s SampleCount = %d, want 5", row.FeatureName, row.SampleCount)
		}
	}
}

func TestRecomputeCorpusPercentiles_EmptyCorpusIsNoop(t *testing.T) {
	empty := map[string][]float64{}
	for _, name := range AllFeatureNames {
		empty[name] = nil
	}
	repo := &fakeAnalysisRepo{values: empty}

	_, sampleCount, err := RecomputeCorpusPercentiles(context.Background(), repo)
	if err != nil {
		t.Fatalf("RecomputeCorpusPercentiles returned error: %v", err)
	}
	if sampleCount != 0 {
		t.Errorf("sampleCount = %d, want 0", sampleCount)
	}
	if repo.upsertCallCnt != 0 {
		t.Errorf("UpsertFeaturePercentiles should not be called for empty corpus, got %d calls", repo.upsertCallCnt)
	}
}
