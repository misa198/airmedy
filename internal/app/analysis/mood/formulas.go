package mood

// RawFeatures is the subset of domain.TrackFeatures the formulas consume.
// Kept as its own struct (rather than taking *domain.TrackFeatures
// directly) so this file stays decoupled from the domain package and the
// formulas are trivially testable with literals.
type RawFeatures struct {
	RMS              float64
	SpectralCentroid float64
	SpectralFlux     float64
	Tempo            float64
	Crest            float64
	OnsetVariance    float64
	LoudnessRange    float64
}

// PercentileSet is the cached corpus percentile table, keyed by raw feature
// name (see AllFeatureNames).
type PercentileSet map[string]Percentile

// Feature name constants, matching feature_percentiles.feature_name and the
// track_features columns they're computed from.
const (
	FeatureRMS              = "rms"
	FeatureSpectralCentroid = "spectral_centroid"
	FeatureSpectralFlux     = "spectral_flux"
	FeatureTempo            = "tempo"
	FeatureCrest            = "crest"
	FeatureOnsetVariance    = "onset_variance"
	FeatureLoudnessRange    = "loudness_range"
)

// AllFeatureNames lists every feature RecomputeCorpusPercentiles computes
// and persists. Single source of truth for the percentile-pull query and
// for tests asserting completeness.
var AllFeatureNames = []string{
	FeatureRMS, FeatureSpectralCentroid, FeatureSpectralFlux,
	FeatureTempo, FeatureCrest, FeatureOnsetVariance, FeatureLoudnessRange,
}

// energyTempoCap is the tempo ceiling applied before normalizing tempo in
// DeriveEnergy (locked formula: norm(tempo, cap=180)).
const energyTempoCap = 180.0

// DeriveEnergy computes the locked energy formula from raw features and the
// cached corpus percentiles. A pctl entry missing for a feature is treated
// as Percentile{} (Normalize's degenerate-spread branch then returns 0.5).
func DeriveEnergy(f RawFeatures, pctl PercentileSet) float64 {
	tempo := f.Tempo
	if tempo > energyTempoCap {
		tempo = energyTempoCap
	}
	return 0.32*Normalize(f.RMS, pctl[FeatureRMS], sigmoidSteepness) +
		0.23*Normalize(f.SpectralCentroid, pctl[FeatureSpectralCentroid], sigmoidSteepness) +
		0.17*Normalize(f.SpectralFlux, pctl[FeatureSpectralFlux], sigmoidSteepness) +
		0.18*Normalize(tempo, pctl[FeatureTempo], sigmoidSteepness) +
		0.10*(1-Normalize(f.Crest, pctl[FeatureCrest], sigmoidSteepness))
}

// DeriveDanceability computes the locked danceability formula.
func DeriveDanceability(f RawFeatures, pctl PercentileSet) float64 {
	return 0.45*tempoScore(f.Tempo) +
		0.30*(1-Normalize(f.OnsetVariance, pctl[FeatureOnsetVariance], sigmoidSteepness)) +
		0.15*(1-Normalize(f.Crest, pctl[FeatureCrest], sigmoidSteepness)) +
		0.10*(1-Normalize(f.LoudnessRange, pctl[FeatureLoudnessRange], sigmoidSteepness))
}

// DeriveBrightness maps spectral centroid, the raw proxy for perceived spectral
// brightness, onto the corpus-relative [0,1] mood scale used by Mood Radio.
func DeriveBrightness(f RawFeatures, pctl PercentileSet) float64 {
	return Normalize(f.SpectralCentroid, pctl[FeatureSpectralCentroid], sigmoidSteepness)
}

// tempoScore is a self-contained triangular function on raw BPM, NOT routed
// through Normalize/percentiles: 0 at <=60bpm, rises linearly to 1 at
// tempoScorePeak (~115bpm), falls linearly back to 0 at >=180bpm.
const (
	tempoScoreLow  = 60.0
	tempoScorePeak = 115.0
	tempoScoreHigh = 180.0
)

func tempoScore(bpm float64) float64 {
	switch {
	case bpm <= tempoScoreLow || bpm >= tempoScoreHigh:
		return 0
	case bpm <= tempoScorePeak:
		return (bpm - tempoScoreLow) / (tempoScorePeak - tempoScoreLow)
	default:
		return (tempoScoreHigh - bpm) / (tempoScoreHigh - tempoScorePeak)
	}
}
