package player

import (
	"log/slog"
	"sync"

	"changeme/internal/domain"
	"github.com/wailsapp/wails/v3/pkg/application"
)

// PlayerService coordinates playback and queue management.
type PlayerService struct {
	mu     sync.RWMutex
	player domain.AudioPlayer
	queue  *QueueService
	logger *slog.Logger
}

func NewPlayerService(player domain.AudioPlayer, queue *QueueService, logger *slog.Logger) *PlayerService {
	s := &PlayerService{
		player: player,
		queue:  queue,
		logger: logger,
	}
	s.player.OnTrackEnd(s.HandleTrackEnd)
	return s
}

// Play starts or resumes playback.
func (s *PlayerService) Play() error {
	return s.player.Play()
}

// Pause pauses playback.
func (s *PlayerService) Pause() error {
	return s.player.Pause()
}

// Stop stops playback.
func (s *PlayerService) Stop() error {
	return s.player.Stop()
}

// Next plays the next track in the queue.
func (s *PlayerService) Next() error {
	track := s.queue.Next()
	if track == nil {
		return s.player.Stop()
	}
	return s.loadAndPlay(track)
}

// Previous plays the previous track in the queue.
func (s *PlayerService) Previous() error {
	track := s.queue.Previous()
	if track == nil {
		return s.player.Stop()
	}
	return s.loadAndPlay(track)
}

// Seek moves playback to the specified position in seconds.
func (s *PlayerService) Seek(position float64) error {
	return s.player.Seek(position)
}

// SetVolume sets the playback volume (0.0 to 1.0).
func (s *PlayerService) SetVolume(volume float64) error {
	return s.player.SetVolume(volume)
}

// SetMuted mutes or unmutes playback.
func (s *PlayerService) SetMuted(muted bool) error {
	return s.player.SetMuted(muted)
}

// PlayTracks sets a new queue and starts playing from the specified index.
func (s *PlayerService) PlayTracks(tracks []*domain.TrackDTO, startIndex int) error {
	s.queue.SetQueue(tracks, startIndex)
	track := s.queue.GetCurrentTrack()
	if track == nil {
		return nil
	}
	return s.loadAndPlay(track)
}

// SetShuffle enables or disables shuffling.
func (s *PlayerService) SetShuffle(enabled bool) error {
	s.queue.SetShuffle(enabled)
	s.emitStatus()
	return nil
}

// SetRepeatMode sets the repeat mode.
func (s *PlayerService) SetRepeatMode(mode domain.RepeatMode) error {
	s.queue.SetRepeatMode(mode)
	s.emitStatus()
	return nil
}

// GetStatus returns the current status of the player.
func (s *PlayerService) GetStatus() domain.PlayerStatus {
	return s.player.GetStatus()
}

// GetQueue returns the current queue.
func (s *PlayerService) GetQueue() []*domain.TrackDTO {
	return s.queue.GetQueue()
}

// Internal helpers

func (s *PlayerService) loadAndPlay(track *domain.TrackDTO) error {
	if err := s.player.Load(track); err != nil {
		s.logger.Error("failed to load track", "track", track.Path, "error", err)
		return err
	}
	if err := s.player.Play(); err != nil {
		s.logger.Error("failed to play track", "track", track.Path, "error", err)
		return err
	}
	s.emitStatus()
	return nil
}

func (s *PlayerService) emitStatus() {
	app := application.Get()
	if app == nil {
		return
	}
	// Get current status from player
	status := s.player.GetStatus()
	// Enrich status with queue info
	status.RepeatMode = s.queue.repeatMode
	status.Shuffle = s.queue.shuffle

	app.Event.Emit("player:status", status)
}

// HandleTrackEnd is called by the native player when a track finishes playing.
func (s *PlayerService) HandleTrackEnd() {
	s.logger.Info("track ended, moving to next")
	if err := s.Next(); err != nil {
		s.logger.Error("failed to play next track", "error", err)
	}
}
