package player

import (
	"context"
	"log/slog"
	"sync"
	"time"

	"changeme/internal/domain"
	"changeme/internal/infra/artwork"
	"github.com/wailsapp/wails/v3/pkg/application"
	"go.uber.org/fx"
)

// PlayerService coordinates playback and queue management.
type PlayerService struct {
	mu           sync.RWMutex
	player       domain.AudioPlayer
	queue        *QueueService
	logger       *slog.Logger
	artworkCache domain.ArtworkCache
	nowPlaying   domain.NowPlayingController // nil on non-darwin or when unsupported
	currentTrack *domain.TrackDTO

	tickerMu     sync.Mutex
	tickerCancel context.CancelFunc
	tickInterval time.Duration

	// emitStatusHook overrides event emission in tests (nil in production).
	emitStatusHook func()
}

func NewPlayerService(
	player domain.AudioPlayer,
	queue *QueueService,
	logger *slog.Logger,
	artworkCache domain.ArtworkCache,
	lc fx.Lifecycle,
) *PlayerService {
	s := &PlayerService{
		player:       player,
		queue:        queue,
		logger:       logger,
		artworkCache: artworkCache,
		tickInterval: 500 * time.Millisecond,
	}
	s.player.OnTrackEnd(s.HandleTrackEnd)

	if npc, ok := player.(domain.NowPlayingController); ok {
		s.nowPlaying = npc
		// Wrap in goroutines: MPRemoteCommandCenter callbacks fire on the macOS
		// main thread. Calling app.Event.Emit() from there deadlocks because
		// Wails also needs the main thread to dispatch to the WebView.
		// Spawning a goroutine hands the work to the Go scheduler immediately,
		// freeing the main thread and letting the Wails event reach the frontend.
		npc.SetRemoteCallbacks(
			func() { go func() { _ = s.Play() }() },
			func() { go func() { _ = s.Pause() }() },
			func() { go func() { _ = s.Next() }() },
			func() { go func() { _ = s.Previous() }() },
		)
		npc.SetupRemoteCommands()
	}

	lc.Append(fx.Hook{
		OnStop: func(_ context.Context) error {
			s.stopPositionTicker()
			return nil
		},
	})

	return s
}

// Play starts or resumes playback.
func (s *PlayerService) Play() error {
	err := s.player.Play()
	if err == nil {
		s.startPositionTicker()
		s.emitStatus()
	}
	return err
}

// Pause pauses playback.
func (s *PlayerService) Pause() error {
	err := s.player.Pause()
	if err == nil {
		s.stopPositionTicker()
		s.emitStatus()
	}
	return err
}

// Stop stops playback.
func (s *PlayerService) Stop() error {
	err := s.player.Stop()
	if err == nil {
		s.stopPositionTicker()
		if s.nowPlaying != nil {
			s.nowPlaying.ClearNowPlaying()
		}
		s.emitStatus()
	}
	return err
}

// Next plays the next track in the queue.
func (s *PlayerService) Next() error {
	track := s.queue.Next()
	if track == nil {
		return s.Stop()
	}
	return s.loadAndPlay(track)
}

// Previous plays the previous track in the queue.
func (s *PlayerService) Previous() error {
	track := s.queue.Previous()
	if track == nil {
		return s.Stop()
	}
	return s.loadAndPlay(track)
}

// Seek moves playback to the specified position in seconds.
func (s *PlayerService) Seek(position float64) error {
	err := s.player.Seek(position)
	if err == nil {
		s.emitStatus()
	}
	return err
}

// SetVolume sets the playback volume (0.0 to 1.0).
func (s *PlayerService) SetVolume(volume float64) error {
	err := s.player.SetVolume(volume)
	if err == nil {
		s.emitStatus()
	}
	return err
}

// SetMuted mutes or unmutes playback.
func (s *PlayerService) SetMuted(muted bool) error {
	err := s.player.SetMuted(muted)
	if err == nil {
		s.emitStatus()
	}
	return err
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
	status := s.player.GetStatus()
	status.RepeatMode = s.queue.repeatMode
	status.Shuffle = s.queue.shuffle
	return status
}

// GetQueue returns the current queue.
func (s *PlayerService) GetQueue() []*domain.TrackDTO {
	return s.queue.GetQueue()
}

// Internal helpers

func (s *PlayerService) loadAndPlay(track *domain.TrackDTO) error {
	s.stopPositionTicker()

	if err := s.player.Load(track); err != nil {
		s.logger.Error("failed to load track", "track", track.Path, "error", err)
		return err
	}
	if err := s.player.Play(); err != nil {
		s.logger.Error("failed to play track", "track", track.Path, "error", err)
		return err
	}

	s.mu.Lock()
	s.currentTrack = track
	s.mu.Unlock()

	s.startPositionTicker()
	s.emitStatus()

	if s.nowPlaying != nil {
		artworkPath := ""
		if track.ArtworkKey != "" {
			artworkPath = s.artworkCache.GetPath(track.ArtworkKey)
		}
		s.nowPlaying.UpdateNowPlaying(track, 0, artworkPath)
	}

	go s.extractAndEmitPalette(track)

	return nil
}

func (s *PlayerService) extractAndEmitPalette(track *domain.TrackDTO) {
	if track.ArtworkKey == "" {
		return
	}
	path := s.artworkCache.GetPath(track.ArtworkKey)
	colors, err := artwork.ExtractPalette(path)
	if err != nil {
		s.logger.Warn("palette extraction failed", "error", err)
		return
	}
	app := application.Get()
	if app != nil {
		app.Event.Emit("player:theme", colors)
	}
}

func (s *PlayerService) emitStatus() {
	if s.emitStatusHook != nil {
		s.emitStatusHook()
		return
	}
	app := application.Get()
	if app == nil {
		return
	}
	status := s.player.GetStatus()
	status.RepeatMode = s.queue.repeatMode
	status.Shuffle = s.queue.shuffle
	app.Event.Emit("player:status", status)
}

func (s *PlayerService) startPositionTicker() {
	s.tickerMu.Lock()
	defer s.tickerMu.Unlock()

	if s.tickerCancel != nil {
		s.tickerCancel()
	}

	ctx, cancel := context.WithCancel(context.Background())
	s.tickerCancel = cancel

	go func() {
		ticker := time.NewTicker(s.tickInterval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				s.mu.RLock()
				track := s.currentTrack
				s.mu.RUnlock()

				s.emitStatus()

				if s.nowPlaying != nil && track != nil {
					status := s.player.GetStatus()
					artworkPath := ""
					if track.ArtworkKey != "" {
						artworkPath = s.artworkCache.GetPath(track.ArtworkKey)
					}
					s.nowPlaying.UpdateNowPlaying(track, status.Position, artworkPath)
				}
			}
		}
	}()
}

func (s *PlayerService) stopPositionTicker() {
	s.tickerMu.Lock()
	defer s.tickerMu.Unlock()
	if s.tickerCancel != nil {
		s.tickerCancel()
		s.tickerCancel = nil
	}
}

// HandleTrackEnd is called by the native player when a track finishes playing.
func (s *PlayerService) HandleTrackEnd() {
	s.stopPositionTicker()
	s.logger.Info("track ended, moving to next")
	if err := s.Next(); err != nil {
		s.logger.Error("failed to play next track", "error", err)
	}
}
