//go:build !darwin && !windows && !linux

package audio

import (
	"log/slog"

	"airmedy/internal/domain"
)

// MockPlayer is a no-op implementation of domain.AudioPlayer for non-macOS platforms or testing.
type MockPlayer struct {
	logger *slog.Logger
	status domain.PlayerStatus
}

func NewPlayer(logger *slog.Logger) domain.AudioPlayer {
	return &MockPlayer{
		logger: logger,
		status: domain.PlayerStatus{
			PlaybackState: domain.PlaybackStateStopped,
			Volume:        1.0,
		},
	}
}

func (p *MockPlayer) Play() error {
	p.logger.Info("MockPlayer: Play")
	p.status.PlaybackState = domain.PlaybackStatePlaying
	return nil
}

func (p *MockPlayer) Pause() error {
	p.logger.Info("MockPlayer: Pause")
	p.status.PlaybackState = domain.PlaybackStatePaused
	return nil
}

func (p *MockPlayer) Stop() error {
	p.logger.Info("MockPlayer: Stop")
	p.status.PlaybackState = domain.PlaybackStateStopped
	return nil
}

func (p *MockPlayer) Seek(position float64) error {
	p.logger.Info("MockPlayer: Seek", "position", position)
	p.status.Position = position
	return nil
}

func (p *MockPlayer) SetVolume(volume float64) error {
	p.logger.Info("MockPlayer: SetVolume", "volume", volume)
	p.status.Volume = volume
	return nil
}

func (p *MockPlayer) SetMuted(muted bool) error {
	p.logger.Info("MockPlayer: SetMuted", "muted", muted)
	p.status.Muted = muted
	return nil
}

func (p *MockPlayer) Load(track *domain.TrackDTO) error {
	p.logger.Info("MockPlayer: Load", "track", track.Path)
	p.status.TrackID = track.ID
	p.status.Duration = float64(track.Duration)
	p.status.Position = 0
	return nil
}

func (p *MockPlayer) Unload() error {
	p.logger.Info("MockPlayer: Unload")
	p.status.TrackID = ""
	return nil
}

func (p *MockPlayer) GetStatus() domain.PlayerStatus {
	return p.status
}

func (p *MockPlayer) OnTrackEnd(callback func()) {
	// Not implemented for mock
}
