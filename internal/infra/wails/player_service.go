package wails

import (
	"context"

	"airmedy/internal/app/player"
	"airmedy/internal/domain"
)

// PlayerService is the Wails binding for playback controls.
type PlayerService struct {
	service *player.PlayerService
}

func NewPlayerService(service *player.PlayerService) *PlayerService {
	return &PlayerService{service: service}
}

func (s *PlayerService) GetService() *player.PlayerService {
	return s.service
}

func (s *PlayerService) Play() error {
	return s.service.Play()
}

func (s *PlayerService) Pause() error {
	return s.service.Pause()
}

func (s *PlayerService) Stop() error {
	return s.service.Stop()
}

func (s *PlayerService) Next() error {
	return s.service.Next()
}

func (s *PlayerService) Previous() error {
	return s.service.Previous()
}

func (s *PlayerService) TogglePause() error {
	return s.service.TogglePause()
}

func (s *PlayerService) FastForward() error {
	return s.service.FastForward()
}

func (s *PlayerService) Rewind() error {
	return s.service.Rewind()
}

func (s *PlayerService) IncreaseVolume() error {
	return s.service.IncreaseVolume()
}

func (s *PlayerService) DecreaseVolume() error {
	return s.service.DecreaseVolume()
}

func (s *PlayerService) ToggleMute() error {
	return s.service.ToggleMute()
}

func (s *PlayerService) Seek(position float64) error {
	return s.service.Seek(position)
}

func (s *PlayerService) SetVolume(volume float64) error {
	return s.service.SetVolume(volume)
}

func (s *PlayerService) SetMuted(muted bool) error {
	return s.service.SetMuted(muted)
}

func (s *PlayerService) PlayTracks(tracks []*domain.TrackDTO, startIndex int) error {
	return s.service.PlayTracks(tracks, startIndex)
}

func (s *PlayerService) PlayTrackIDs(trackIDs []string, startIndex int) error {
	return s.service.PlayTrackIDs(context.Background(), trackIDs, startIndex)
}

func (s *PlayerService) ShuffleTracks(tracks []*domain.TrackDTO) error {
	return s.service.ShuffleTracks(tracks)
}

func (s *PlayerService) ShuffleTrackIDs(trackIDs []string) error {
	return s.service.ShuffleTrackIDs(context.Background(), trackIDs)
}

func (s *PlayerService) SetShuffle(enabled bool) error {
	return s.service.SetShuffle(enabled)
}

func (s *PlayerService) SetRepeatMode(mode string) error {
	return s.service.SetRepeatMode(domain.RepeatMode(mode))
}

func (s *PlayerService) GetStatus() domain.PlayerStatus {
	return s.service.GetStatus()
}

func (s *PlayerService) GetQueue() []*domain.TrackDTO {
	return s.service.GetQueue()
}

func (s *PlayerService) PlayNext(track *domain.TrackDTO) {
	s.service.PlayNext(track)
}

func (s *PlayerService) PlayNextTracks(tracks []*domain.TrackDTO) {
	s.service.PlayNextTracks(tracks)
}

func (s *PlayerService) RemoveFromQueue(trackID string) {
	s.service.RemoveFromQueue(trackID)
}

func (s *PlayerService) PlayQueueIndex(index int) error {
	return s.service.PlayQueueIndex(index)
}

func (s *PlayerService) ReorderQueue(tracks []*domain.TrackDTO) {
	ids := make([]string, len(tracks))
	for i, t := range tracks {
		ids[i] = t.ID
	}
	s.service.ReorderQueue(ids)
}

func (s *PlayerService) ReorderQueueIDs(trackIDs []string) {
	s.service.ReorderQueue(trackIDs)
}
