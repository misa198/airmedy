package player

import (
	"context"
	"fmt"
	"log/slog"
	"math/rand"
	"os"
	"sync"
	"time"

	"airmedy/internal/app/lyrics"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"

	"github.com/wailsapp/wails/v3/pkg/application"
	"go.uber.org/fx"
)

// PlayerService coordinates playback and queue management.
type PlayerService struct {
	mu            sync.RWMutex
	player        domain.AudioPlayer
	queue         *QueueService
	logger        *slog.Logger
	artworkCache  domain.ArtworkCache
	lyricsService *lyrics.LyricsService
	nowPlaying    domain.NowPlayingController // nil on non-darwin or when unsupported
	currentTrack  *domain.TrackDTO
	currentTheme  *domain.ThemeColors
	trackRepo     domain.TrackRepository
	stateRepo     domain.PlayerStateRepository

	tickerMu     sync.Mutex
	tickerCancel context.CancelFunc
	tickInterval time.Duration

	endedNaturally bool // true when queue ran out; cleared on Play or loadAndPlay

	// emitStatusHook overrides event emission in tests (nil in production).
	emitStatusHook func()

	statusListeners []func(domain.PlayerStatus)
	queueListeners  []func([]*domain.TrackDTO)
}

func NewPlayerService(
	player domain.AudioPlayer,
	queue *QueueService,
	logger *slog.Logger,
	artworkCache domain.ArtworkCache,
	lyricsService *lyrics.LyricsService,
	trackRepo domain.TrackRepository,
	stateRepo domain.PlayerStateRepository,
	lc fx.Lifecycle,
) *PlayerService {
	s := &PlayerService{
		player:        player,
		queue:         queue,
		logger:        logger,
		artworkCache:  artworkCache,
		lyricsService: lyricsService,
		trackRepo:     trackRepo,
		stateRepo:     stateRepo,
		tickInterval:  500 * time.Millisecond,
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
		OnStart: func(ctx context.Context) error {
			s.restoreState(ctx)
			return nil
		},
		OnStop: func(ctx context.Context) error {
			s.stopPositionTicker()
			s.saveState(ctx)
			return nil
		},
	})

	return s
}

// AddStatusListener registers a callback that will be called whenever the player status changes.
func (s *PlayerService) AddStatusListener(f func(domain.PlayerStatus)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.statusListeners = append(s.statusListeners, f)
}

// AddQueueListener registers a callback that will be called whenever the queue changes.
func (s *PlayerService) AddQueueListener(f func([]*domain.TrackDTO)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.queueListeners = append(s.queueListeners, f)
}

// Play starts or resumes playback. If no track is loaded and the queue is empty,
// loads all library tracks in random order and begins playing.
func (s *PlayerService) Play() error {
	s.mu.Lock()
	ct := s.currentTrack
	ended := s.endedNaturally
	if ended {
		s.endedNaturally = false
	}
	s.mu.Unlock()

	if ct == nil && len(s.queue.GetQueue()) == 0 {
		return s.playAll()
	}

	// Track ended naturally (queue ran out): AVFoundation won't restart a finished
	// item via Play() alone — reload from the beginning.
	if ended && ct != nil {
		return s.loadAndPlay(ct)
	}

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
		s.saveState(context.Background())
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
		s.saveState(context.Background())
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

// PlayTrackIDs fetches tracks by ID from the repository and starts playing from startIndex.
// Preferred over PlayTracks when the caller already has IDs — avoids large IPC serialization.
func (s *PlayerService) PlayTrackIDs(ctx context.Context, trackIDs []string, startIndex int) error {
	tracks, err := s.trackRepo.GetByIDs(ctx, trackIDs)
	if err != nil {
		return fmt.Errorf("failed to fetch tracks by ids: %w", err)
	}
	return s.PlayTracks(tracks, startIndex)
}

// ShuffleTrackIDs fetches tracks by ID from the repository and shuffles them.
func (s *PlayerService) ShuffleTrackIDs(ctx context.Context, trackIDs []string) error {
	tracks, err := s.trackRepo.GetByIDs(ctx, trackIDs)
	if err != nil {
		return fmt.Errorf("failed to fetch tracks by ids: %w", err)
	}
	return s.ShuffleTracks(tracks)
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

// ShuffleTracks shuffles the given tracks and starts playing the first one.
func (s *PlayerService) ShuffleTracks(tracks []*domain.TrackDTO) error {
	s.queue.ShuffleTracks(tracks)
	track := s.queue.GetCurrentTrack()
	if track == nil {
		return nil
	}
	err := s.loadAndPlay(track)
	if err == nil {
		s.emitStatus()
		s.emitQueue()
	}
	return err
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

// PlayNext inserts a track immediately after the currently playing track.
func (s *PlayerService) PlayNext(track *domain.TrackDTO) {
	s.queue.InsertAfterCurrent(track)
	s.emitQueue()
}

// PlayNextTracks inserts a list of tracks immediately after the currently playing track.
func (s *PlayerService) PlayNextTracks(tracks []*domain.TrackDTO) {
	s.queue.InsertListAfterCurrent(tracks)
	s.emitQueue()
}

// RemoveFromQueue removes a track from the queue.
func (s *PlayerService) RemoveFromQueue(trackID string) {
	s.mu.RLock()
	ct := s.currentTrack
	s.mu.RUnlock()

	isCurrent := ct != nil && ct.ID == trackID

	s.queue.RemoveTrack(trackID)
	s.emitQueue()

	if isCurrent {
		track := s.queue.GetCurrentTrack()
		if track != nil {
			_ = s.loadAndPlay(track)
		} else {
			_ = s.Stop()
		}
	}
}

// GetStatus returns the current status of the player.
func (s *PlayerService) GetStatus() domain.PlayerStatus {
	s.mu.RLock()
	defer s.mu.RUnlock()
	status := s.player.GetStatus()
	status.RepeatMode = s.queue.repeatMode
	status.Shuffle = s.queue.shuffle
	status.Theme = s.currentTheme
	return status
}

// GetQueue returns the current queue.
func (s *PlayerService) GetQueue() []*domain.TrackDTO {
	return s.queue.GetQueue()
}

// IsQueueEmpty returns true if the queue has no tracks.
func (s *PlayerService) IsQueueEmpty() bool {
	return s.queue.IsEmpty()
}

// GetCurrentTrack returns the currently playing track.
func (s *PlayerService) GetCurrentTrack() *domain.TrackDTO {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.currentTrack
}

// PeekNextTrack returns the next track in the queue.
func (s *PlayerService) PeekNextTrack() *domain.TrackDTO {
	return s.queue.PeekNext()
}

// PeekPreviousTrack returns the previous track in the queue.
func (s *PlayerService) PeekPreviousTrack() *domain.TrackDTO {
	return s.queue.PeekPrevious()
}

// SyncTrack updates the metadata of a track in the current player state if it matches.
func (s *PlayerService) SyncTrack(track *domain.TrackDTO) {
	s.mu.Lock()
	if s.currentTrack != nil && s.currentTrack.ID == track.ID {
		s.currentTrack.IsFavorite = track.IsFavorite
		// Copy other relevant fields if needed, but for now focus on Favorite
		s.mu.Unlock()
		s.emitStatus()
	} else {
		s.mu.Unlock()
	}

	// Also update in queue if present
	s.queue.UpdateTrack(track)
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
	s.currentTheme = nil
	s.mu.Unlock()

	// Increment play count
	go func(id string) {
		if err := s.trackRepo.IncrementPlayCount(context.Background(), id); err != nil {
			s.logger.Warn("failed to increment play count", "track_id", id, "error", err)
		}
	}(track.ID)

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
	go s.fetchAndEmitLyrics(track)

	s.saveState(context.Background())

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

	s.mu.Lock()
	s.currentTheme = colors
	s.mu.Unlock()

	app := application.Get()
	if app != nil && app.Event != nil {
		defer func() { recover() }()
		app.Event.Emit("player:theme", colors)
	}
}

func (s *PlayerService) fetchAndEmitLyrics(track *domain.TrackDTO) {
	if s.lyricsService == nil {
		return
	}
	ctx := context.Background()

	// Check the database first.
	lyric, err := s.lyricsService.GetLyrics(ctx, track.ID)
	if err != nil {
		s.logger.Warn("failed to get lyrics from db", "track_id", track.ID, "error", err)
	}

	// If not cached, try the external API.
	if lyric == nil {
		lyric, err = s.lyricsService.FetchFromExternal(ctx, track)
		if err != nil {
			s.logger.Warn("failed to fetch lyrics from external", "track_id", track.ID, "error", err)
		}
	}

	a := application.Get()
	if a == nil || a.Event == nil {
		return
	}
	defer func() { recover() }()
	if lyric != nil {
		a.Event.Emit("player:lyrics", lyric)
	} else {
		a.Event.Emit("player:lyrics", nil)
	}
}

func (s *PlayerService) emitStatus() {
	if s.emitStatusHook != nil {
		s.emitStatusHook()
		return
	}
	status := s.GetStatus()

	s.mu.RLock()
	listeners := make([]func(domain.PlayerStatus), len(s.statusListeners))
	copy(listeners, s.statusListeners)
	s.mu.RUnlock()

	for _, f := range listeners {
		f(status)
	}

	app := application.Get()
	if app == nil || app.Event == nil {
		return
	}
	app.Event.Emit("player:status", status)
}

func (s *PlayerService) emitQueue() {
	queue := s.queue.GetQueue()

	s.mu.RLock()
	listeners := make([]func([]*domain.TrackDTO), len(s.queueListeners))
	copy(listeners, s.queueListeners)
	s.mu.RUnlock()

	for _, f := range listeners {
		f(queue)
	}

	app := application.Get()
	if app != nil && app.Event != nil {
		app.Event.Emit("player:queue-updated", queue)
	}
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
	s.logger.Debug("track ended, moving to next")
	track := s.queue.Next()
	if track == nil {
		s.mu.Lock()
		s.endedNaturally = true
		s.mu.Unlock()
		if err := s.Stop(); err != nil {
			s.logger.Error("failed to stop after queue end", "error", err)
		}
		return
	}
	if err := s.loadAndPlay(track); err != nil {
		s.logger.Error("failed to play next track", "error", err)
	}
}

func (s *PlayerService) playAll() error {
	ctx := context.Background()
	tracks, err := s.trackRepo.GetAll(ctx)
	if err != nil {
		return fmt.Errorf("failed to load library tracks: %w", err)
	}
	if len(tracks) == 0 {
		return nil
	}

	rng := rand.New(rand.NewSource(time.Now().UnixNano()))
	rng.Shuffle(len(tracks), func(i, j int) { tracks[i], tracks[j] = tracks[j], tracks[i] })

	s.queue.SetQueue(tracks, 0)
	s.emitQueue()

	track := s.queue.GetCurrentTrack()
	if track == nil {
		return nil
	}
	return s.loadAndPlay(track)
}

func (s *PlayerService) saveState(ctx context.Context) {
	s.mu.RLock()
	ct := s.currentTrack
	s.mu.RUnlock()

	queue := s.queue.GetQueue()
	ids := make([]string, len(queue))
	for i, t := range queue {
		ids[i] = t.ID
	}

	status := s.player.GetStatus()

	currentID := ""
	if ct != nil {
		currentID = ct.ID
	}

	state := &domain.PlayerState{
		QueueTrackIDs:  ids,
		CurrentTrackID: currentID,
		Position:       status.Position,
		Volume:         status.Volume,
		Muted:          status.Muted,
		Shuffle:        s.queue.shuffle,
		RepeatMode:     s.queue.repeatMode,
	}
	if err := s.stateRepo.Save(ctx, state); err != nil {
		s.logger.Error("failed to save player state", "error", err)
	}
}

func (s *PlayerService) restoreState(ctx context.Context) {
	state, err := s.stateRepo.Load(ctx)
	if err != nil {
		s.logger.Error("failed to load player state", "error", err)
		return
	}
	if state == nil || len(state.QueueTrackIDs) == 0 {
		return
	}

	var validTracks []*domain.TrackDTO
	for _, id := range state.QueueTrackIDs {
		track, err := s.trackRepo.GetByID(ctx, id)
		if err != nil || track == nil {
			continue
		}
		if _, err := os.Stat(track.Path); err != nil {
			continue
		}
		validTracks = append(validTracks, track)
	}

	if len(validTracks) == 0 {
		return
	}

	s.queue.SetRepeatMode(state.RepeatMode)
	if state.Shuffle {
		s.queue.SetShuffle(true)
	}

	currentIndex := 0
	var currentTrack *domain.TrackDTO
	for i, t := range validTracks {
		if t.ID == state.CurrentTrackID {
			currentIndex = i
			currentTrack = t
			break
		}
	}

	s.queue.SetQueue(validTracks, currentIndex)

	if currentTrack == nil {
		return
	}

	if err := s.player.Load(currentTrack); err != nil {
		s.logger.Error("failed to load track on restore", "track", currentTrack.Path, "error", err)
		return
	}
	if err := s.player.Seek(state.Position); err != nil {
		s.logger.Warn("failed to seek to saved position on restore", "error", err)
	}
	if err := s.player.SetVolume(state.Volume); err != nil {
		s.logger.Warn("failed to restore volume", "error", err)
	}
	if err := s.player.SetMuted(state.Muted); err != nil {
		s.logger.Warn("failed to restore mute state", "error", err)
	}

	s.mu.Lock()
	s.currentTrack = currentTrack
	s.mu.Unlock()

	go s.extractAndEmitPalette(currentTrack)
	go s.fetchAndEmitLyrics(currentTrack)

	s.emitStatus()
}
