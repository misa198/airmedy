package normalization

import (
	"context"
	"fmt"
	"log/slog"
	"math"

	"airmedy/internal/domain"
)

// Acceptable range for a user-configured target loudness. Wider than typical
// streaming targets (-23..-9) but rejects nonsensical input (NaN, Inf, wildly
// off values from a malformed request).
const (
	minTargetLUFS = -40.0
	maxTargetLUFS = -5.0
)

// NormalizationService computes and applies per-track pre-amp gain so playback
// hits the user's target loudness, using the loudness/true-peak features the
// analysis pipeline writes to track_features.
type NormalizationService struct {
	settings domain.SettingsRepository
	analysis domain.AnalysisRepository
	tracks   domain.TrackRepository
	player   domain.NormalizationController // nil if the audio player doesn't support it
	logger   *slog.Logger
}

func NewNormalizationService(settings domain.SettingsRepository, analysis domain.AnalysisRepository,
	tracks domain.TrackRepository, player domain.AudioPlayer, logger *slog.Logger) *NormalizationService {
	var ctrl domain.NormalizationController
	if c, ok := player.(domain.NormalizationController); ok {
		ctrl = c
	}
	return &NormalizationService{settings: settings, analysis: analysis, tracks: tracks, player: ctrl, logger: logger}
}

func (s *NormalizationService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	return s.settings.Load(ctx)
}

func (s *NormalizationService) SetEnabled(ctx context.Context, enabled bool) error {
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return err
	}
	if enabled && !settings.LibraryAnalysisEnabled {
		return fmt.Errorf("library analysis must be enabled before normalization")
	}
	settings.NormalizationEnabled = enabled
	s.logger.Debug("normalization: enabled changed", "enabled", enabled)
	return s.settings.Save(ctx, settings)
}

func (s *NormalizationService) SetMode(ctx context.Context, mode string) error {
	if mode != "off" && mode != "track" && mode != "album" {
		return fmt.Errorf("invalid normalization mode: %s", mode)
	}
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return err
	}
	settings.NormalizationMode = mode
	s.logger.Debug("normalization: mode changed", "mode", mode)
	return s.settings.Save(ctx, settings)
}

func (s *NormalizationService) SetTarget(ctx context.Context, targetLUFS float64) error {
	if math.IsNaN(targetLUFS) || math.IsInf(targetLUFS, 0) || targetLUFS < minTargetLUFS || targetLUFS > maxTargetLUFS {
		return fmt.Errorf("invalid normalization target LUFS: %v (must be between %v and %v)", targetLUFS, minTargetLUFS, maxTargetLUFS)
	}
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return err
	}
	settings.NormalizationTargetLUFS = targetLUFS
	s.logger.Debug("normalization: target LUFS changed", "target_lufs", targetLUFS)
	return s.settings.Save(ctx, settings)
}

func (s *NormalizationService) SetPreventClip(ctx context.Context, enabled bool) error {
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return err
	}
	settings.NormalizationPreventClip = enabled
	s.logger.Debug("normalization: prevent-clip changed", "enabled", enabled)
	return s.settings.Save(ctx, settings)
}

// sameAlbumChain reports whether next continues the same album run as cur,
// i.e. album-mode gain should use the album average rather than falling back
// to per-track gain. False at the end of the queue (next == nil) or when the
// album changes — both are "seam" points where a shared album gain could
// under/overshoot badly for the listener. Tracks with no album metadata
// (cur.AlbumID == "") are also excluded so that two unrelated untagged files
// don't get treated as "the same album" via a shared empty string.
func sameAlbumChain(cur, next *domain.TrackDTO) bool {
	if cur.AlbumID == "" {
		return false
	}
	if next == nil {
		return false
	}
	return next.AlbumID == cur.AlbumID
}

// ComputeGain returns the pre-amp gain (dB) for track, and whether the track
// has stored loudness features. hasFeatures is false when analysis hasn't run
// yet for this track (gain is 0 in that case — plays at normal volume). next
// is the track that will play immediately after track (nil at the end of the
// queue); it drives the album-mode look-ahead in sameAlbumChain.
func (s *NormalizationService) ComputeGain(ctx context.Context, track, next *domain.TrackDTO) (gainDB float64, hasFeatures bool, err error) {
	settings, err := s.settings.Load(ctx)
	if err != nil {
		return 0, false, err
	}
	if !settings.LibraryAnalysisEnabled || !settings.NormalizationEnabled || settings.NormalizationMode == "off" {
		return 0, true, nil
	}

	f, err := s.analysis.GetFeatures(ctx, track.ID)
	if err != nil {
		return 0, false, err
	}
	if f == nil {
		s.logger.Debug("normalization: no features yet, skipping gain", "track", track.ID)
		return 0, false, nil
	}

	targetLUFS := settings.NormalizationTargetLUFS
	loudness := f.LoudnessLUFS

	if settings.NormalizationMode == "album" && sameAlbumChain(track, next) {
		loudness = s.albumLoudness(ctx, track.AlbumID, f.LoudnessLUFS)
	}

	gain := targetLUFS - loudness

	if settings.NormalizationPreventClip {
		if maxGain := -f.TruePeak; gain > maxGain {
			gain = maxGain
		}
	}

	return gain, true, nil
}

// albumLoudness averages the LUFS of every analyzed track on the album,
// falling back to selfLUFS if no sibling has been analyzed yet. Applying the
// same gain to every track on the album (rather than per-track) preserves
// the original relative loudness between tracks.
func (s *NormalizationService) albumLoudness(ctx context.Context, albumID string, selfLUFS float64) float64 {
	siblings, err := s.tracks.GetByAlbumID(ctx, albumID)
	if err != nil || len(siblings) == 0 {
		return selfLUFS
	}

	var sum float64
	var count int
	for _, t := range siblings {
		f, err := s.analysis.GetFeatures(ctx, t.ID)
		if err != nil || f == nil {
			continue
		}
		sum += f.LoudnessLUFS
		count++
	}
	if count == 0 {
		return selfLUFS
	}
	return sum / float64(count)
}

// ApplyToPlayer computes and pushes the pre-amp gain for track to the audio
// player. next is the track that will play immediately after track (nil at
// the end of the queue); see ComputeGain. No-op if the player doesn't support
// pre-amp gain.
func (s *NormalizationService) ApplyToPlayer(ctx context.Context, track, next *domain.TrackDTO) {
	if s.player == nil || track == nil {
		return
	}
	gain, _, err := s.ComputeGain(ctx, track, next)
	if err != nil {
		s.logger.Warn("failed to compute normalization gain", "track", track.ID, "error", err)
		gain = 0
	}
	if err := s.player.SetPreampGain(gain); err != nil {
		s.logger.Warn("failed to set preamp gain", "track", track.ID, "gain", gain, "error", err)
		return
	}
	s.logger.Debug("normalization: applied preamp gain", "track", track.ID, "gain_db", gain)
}
