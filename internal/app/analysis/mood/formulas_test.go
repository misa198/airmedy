package mood

import "testing"

func approxEqual(a, b, eps float64) bool {
	d := a - b
	if d < 0 {
		d = -d
	}
	return d <= eps
}

func TestTempoScore_KnownValues(t *testing.T) {
	cases := []struct {
		bpm  float64
		want float64
	}{
		{0, 0},
		{60, 0},
		{115, 1},
		{180, 0},
		{200, 0},
		{87.5, 0.5},  // midpoint between 60 and 115
		{147.5, 0.5}, // midpoint between 115 and 180
	}
	for _, c := range cases {
		got := tempoScore(c.bpm)
		if !approxEqual(got, c.want, 1e-9) {
			t.Errorf("tempoScore(%v) = %v, want %v", c.bpm, got, c.want)
		}
	}
}

func TestTempoScore_Monotonic(t *testing.T) {
	rising := []float64{60, 70, 90, 115}
	for i := 1; i < len(rising); i++ {
		if tempoScore(rising[i]) < tempoScore(rising[i-1]) {
			t.Errorf("tempoScore should rise from %v to %v", rising[i-1], rising[i])
		}
	}
	falling := []float64{115, 140, 160, 180}
	for i := 1; i < len(falling); i++ {
		if tempoScore(falling[i]) > tempoScore(falling[i-1]) {
			t.Errorf("tempoScore should fall from %v to %v", falling[i-1], falling[i])
		}
	}
}

func medianPctlSet() PercentileSet {
	p := Percentile{P1: 0, P5: 10, P50: 50, P95: 90, P99: 100}
	return PercentileSet{
		FeatureRMS:              p,
		FeatureSpectralCentroid: p,
		FeatureSpectralFlux:     p,
		FeatureTempo:            p,
		FeatureCrest:            p,
		FeatureOnsetVariance:    p,
		FeatureLoudnessRange:    p,
	}
}

func TestDeriveEnergy_AllInputsAtMedian(t *testing.T) {
	pctl := medianPctlSet()
	f := RawFeatures{RMS: 50, SpectralCentroid: 50, SpectralFlux: 50, Tempo: 50, Crest: 50}
	got := DeriveEnergy(f, pctl)
	// Every Normalize term is 0.5 (at the median); weights sum to 1, so the
	// weighted sum of 0.32/0.23/0.17/0.18*0.5 plus 0.10*(1-0.5) is exactly 0.5.
	if !approxEqual(got, 0.5, 1e-9) {
		t.Errorf("DeriveEnergy at all-median inputs = %v, want 0.5", got)
	}
}

func TestDeriveEnergy_TempoCapClampsBeforeNormalize(t *testing.T) {
	pctl := medianPctlSet()
	pctl[FeatureTempo] = Percentile{P1: 60, P5: 80, P50: 115, P95: 160, P99: 180}

	f180 := RawFeatures{RMS: 50, SpectralCentroid: 50, SpectralFlux: 50, Crest: 50, Tempo: 180}
	f200 := RawFeatures{RMS: 50, SpectralCentroid: 50, SpectralFlux: 50, Crest: 50, Tempo: 200}

	got180 := DeriveEnergy(f180, pctl)
	got200 := DeriveEnergy(f200, pctl)
	if !approxEqual(got180, got200, 1e-12) {
		t.Errorf("energy should be identical once tempo is capped at 180: got180=%v got200=%v", got180, got200)
	}
}

func TestDeriveDanceability_AllInputsAtMedian(t *testing.T) {
	pctl := medianPctlSet()
	f := RawFeatures{Tempo: 115, OnsetVariance: 50, Crest: 50, LoudnessRange: 50}
	got := DeriveDanceability(f, pctl)
	// tempoScore(115) = 1 (peak); onset/crest/loudness Normalize terms = 0.5 -> (1-0.5)=0.5.
	want := 0.45*1 + 0.30*0.5 + 0.15*0.5 + 0.10*0.5
	if !approxEqual(got, want, 1e-9) {
		t.Errorf("DeriveDanceability at median = %v, want %v", got, want)
	}
}

func TestDeriveDanceability_TempoTermUsesTempoScoreNotNormalize(t *testing.T) {
	pctl := medianPctlSet()
	base := RawFeatures{OnsetVariance: 50, Crest: 50, LoudnessRange: 50}

	peak := base
	peak.Tempo = 115 // tempoScore = 1
	fast := base
	fast.Tempo = 300 // tempoScore = 0 (beyond high bound)

	diff := DeriveDanceability(peak, pctl) - DeriveDanceability(fast, pctl)
	if !approxEqual(diff, 0.45, 1e-9) {
		t.Errorf("danceability tempo term should account for exactly the 0.45 weight, got diff=%v", diff)
	}
}
