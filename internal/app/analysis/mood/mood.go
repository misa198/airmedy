package mood

import "airmedy/internal/domain"

// ToRawFeatures maps the subset of domain.TrackFeatures the formulas need.
func ToRawFeatures(f *domain.TrackFeatures) RawFeatures {
	return RawFeatures{
		RMS:              f.RMS,
		SpectralCentroid: f.SpectralCentroid,
		SpectralFlux:     f.SpectralFlux,
		Tempo:            f.Tempo,
		Crest:            f.Crest,
		OnsetVariance:    f.OnsetVariance,
		LoudnessRange:    f.LoudnessRange,
	}
}

// Derive computes energy, danceability, and brightness for f against pctl in one call.
func Derive(f *domain.TrackFeatures, pctl PercentileSet) (energy, danceability, brightness float64) {
	raw := ToRawFeatures(f)
	return DeriveEnergy(raw, pctl), DeriveDanceability(raw, pctl), DeriveBrightness(raw, pctl)
}
