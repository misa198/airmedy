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
	settingsRepo  domain.SettingsRepository

	trackStartTime time.Time
	playCounted    map[string]bool // trackID -> bool
	npReported     map[string]bool // trackID -> bool
	posConfirmed   map[string]bool // trackID -> bool

	tickerMu     sync.Mutex
	tickerCancel context.CancelFunc
	tickInterval time.Duration

	endedNaturally bool             // true when queue ran out; cleared on Play or loadAndPlay
	nextPreQueued  *domain.TrackDTO // track pre-enqueued for gapless transition

	// emitStatusHook overrides event emission in tests (nil in production).
	emitStatusHook func()

	statusListeners   []func(domain.PlayerStatus)
	queueListeners    []func([]*domain.TrackDTO)
	scrobbleListeners []func(*domain.TrackDTO, time.Time)
	npListeners       []func(*domain.TrackDTO)
}

func NewPlayerService(
	player domain.AudioPlayer,
	queue *QueueService,
	logger *slog.Logger,
	artworkCache domain.ArtworkCache,
	lyricsService *lyrics.LyricsService,
	trackRepo domain.TrackRepository,
	stateRepo domain.PlayerStateRepository,
	settingsRepo domain.SettingsRepository,
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
		settingsRepo:  settingsRepo,
		tickInterval:  500 * time.Millisecond,
		playCounted:   make(map[string]bool),
		npReported:    make(map[string]bool),
		posConfirmed:  make(map[string]bool),
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
			func(pos float64) { go func() { _ = s.Seek(pos) }() },
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
			if closer, ok := s.player.(interface{ Close() }); ok {
				closer.Close()
			}
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

// AddScrobbleListener registers a callback that will be called whenever a track is scrobbled.
func (s *PlayerService) AddScrobbleListener(f func(*domain.TrackDTO, time.Time)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.scrobbleListeners = append(s.scrobbleListeners, f)
}

// AddNowPlayingListener registers a callback that will be called when a track is verified as "Now Playing".
func (s *PlayerService) AddNowPlayingListener(f func(*domain.TrackDTO)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.npListeners = append(s.npListeners, f)
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

	// Track ended naturally (queue ran out): SFBAudioEngine won't restart a finished
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
	status := s.player.GetStatus()
	if status.Position > 3 {
		return s.Seek(0)
	}

	track := s.queue.Previous()
	if track == nil {
		return s.Stop()
	}
	return s.loadAndPlay(track)
}

// TogglePause toggles between playing and paused states.
func (s *PlayerService) TogglePause() error {
	status := s.player.GetStatus()
	if status.PlaybackState == domain.PlaybackStatePlaying {
		return s.Pause()
	}
	return s.Play()
}

// FastForward seeks forward by 10 seconds.
func (s *PlayerService) FastForward() error {
	status := s.player.GetStatus()
	newPos := status.Position + 10
	if newPos > status.Duration {
		return s.Next()
	}
	return s.Seek(newPos)
}

// Rewind seeks backward by 10 seconds.
func (s *PlayerService) Rewind() error {
	status := s.player.GetStatus()
	newPos := status.Position - 10
	if newPos < 0 {
		newPos = 0
	}
	return s.Seek(newPos)
}

// IncreaseVolume increases the volume by 5%.
func (s *PlayerService) IncreaseVolume() error {
	status := s.player.GetStatus()
	if status.Muted {
		_ = s.SetMuted(false)
	}
	newVol := status.Volume + 0.05
	if newVol > 1.0 {
		newVol = 1.0
	}
	return s.SetVolume(newVol)
}

// DecreaseVolume decreases the volume by 5%.
func (s *PlayerService) DecreaseVolume() error {
	status := s.player.GetStatus()
	if status.Muted {
		_ = s.SetMuted(false)
	}
	newVol := status.Volume - 0.05
	if newVol < 0 {
		newVol = 0
	}
	return s.SetVolume(newVol)
}

// ToggleMute toggles the mute state.
func (s *PlayerService) ToggleMute() error {
	status := s.player.GetStatus()
	return s.SetMuted(!status.Muted)
}

// Seek moves playback to the specified position in seconds.
func (s *PlayerService) Seek(position float64) error {
	err := s.player.Seek(position)
	if err == nil {
		s.mu.Lock()
		// Adjust trackStartTime so that time.Since(trackStartTime) reflects the seeked position.
		// This ensures stale check logic doesn't block scrobbles if user seeks to > 5s immediately.
		s.trackStartTime = time.Now().Add(-time.Duration(position) * time.Second)
		if s.currentTrack != nil {
			s.posConfirmed[s.currentTrack.ID] = true
		}
		s.mu.Unlock()
		s.emitStatus()
	}
	return err
}

// SetVolume sets the playback volume (0.0 to 1.0).
func (s *PlayerService) SetVolume(volume float64) error {
	status := s.player.GetStatus()
	if status.Muted && volume > 0 {
		_ = s.player.SetMuted(false)
	}
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

	// Re-sync the gapless pre-queue with the new repeat mode so that stale
	// pre-queued tracks don't cause the wrong track to play on the next track-end.
	s.mu.Lock()
	hadPreQueued := s.nextPreQueued != nil
	s.nextPreQueued = nil
	s.mu.Unlock()

	if hadPreQueued {
		if gp, ok := s.player.(domain.GaplessPlayer); ok {
			gp.ClearEnqueued()
			if next := s.queue.PeekNext(); next != nil {
				if err := gp.EnqueueNext(next); err == nil {
					s.mu.Lock()
					s.nextPreQueued = next
					s.mu.Unlock()
				}
			}
		}
	}

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
			s.mu.Lock()
			s.currentTrack = nil
			s.mu.Unlock()
			_ = s.Stop()
		}
	}
}

// PlayQueueIndex plays the track at the given index in the active queue
// without replacing or re-shuffling the queue.
func (s *PlayerService) PlayQueueIndex(index int) error {
	s.queue.SetCurrentIndex(index)
	track := s.queue.GetCurrentTrack()
	if track == nil {
		return fmt.Errorf("no track at queue index %d", index)
	}
	return s.loadAndPlay(track)
}

// ReorderQueue updates the order of tracks in the queue using track IDs.
func (s *PlayerService) ReorderQueue(trackIDs []string) {
	s.queue.ReorderQueue(trackIDs)
	s.emitQueue()
	s.saveState(context.Background())
}

// GetStatus returns the current status of the player.
func (s *PlayerService) GetStatus() domain.PlayerStatus {
	s.mu.RLock()
	defer s.mu.RUnlock()
	status := s.player.GetStatus()
	status.RepeatMode = s.queue.GetRepeatMode()
	status.Shuffle = s.queue.GetShuffle()
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

	// Clear any stale pre-queue — hard load supersedes gapless pre-loading.
	s.mu.Lock()
	s.nextPreQueued = nil
	s.mu.Unlock()
	const gapless = true

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
	s.trackStartTime = time.Now()
	delete(s.playCounted, track.ID)
	delete(s.npReported, track.ID)
	delete(s.posConfirmed, track.ID)
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
	go s.fetchAndEmitLyrics(track)

	s.saveState(context.Background())

	// Pre-enqueue the next track for gapless transitions.
	if gapless {
		if next := s.queue.PeekNext(); next != nil {
			if gp, ok := s.player.(domain.GaplessPlayer); ok {
				if err := gp.EnqueueNext(next); err != nil {
					s.logger.Warn("failed to pre-enqueue next track", "error", err)
				} else {
					s.mu.Lock()
					s.nextPreQueued = next
					s.mu.Unlock()
				}
			}
		}
	}

	return nil
}

// transitionToTrack updates app state when the audio engine has already transitioned
// to track (gapless path). Does NOT call player.Load/Play.
func (s *PlayerService) transitionToTrack(track *domain.TrackDTO) {
	s.mu.Lock()
	s.currentTrack = track
	s.currentTheme = nil
	s.trackStartTime = time.Now()
	delete(s.playCounted, track.ID)
	delete(s.npReported, track.ID)
	delete(s.posConfirmed, track.ID)
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
	go s.fetchAndEmitLyrics(track)

	s.saveState(context.Background())
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
		defer func() { _ = recover() }()
		app.Event.Emit("player:theme", colors)
	}
}

func (s *PlayerService) fetchAndEmitLyrics(track *domain.TrackDTO) {
	if s.lyricsService == nil {
		return
	}
	ctx := context.Background()

	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		settings = &domain.AppSettings{}
	}

	// 1. Try to get best available lyrics according to settings.
	lyric := s.lyricsService.ResolveLyrics(ctx, track.ID, settings.PreferMetadataLyrics)
	if lyric != nil {
		s.emitLyrics(track.ID, lyric)
	}

	// 2. If any provider is enabled and we don't have external lyrics yet, fetch from providers.
	dbLyric, _ := s.lyricsService.GetLyrics(ctx, track.ID)
	hasExternal := dbLyric != nil && dbLyric.Content != ""
	anyProviderEnabled := settings.EnableLrclib || settings.EnableKugou
	if !hasExternal && anyProviderEnabled {
		fetched, err := s.lyricsService.FetchFromProviders(ctx, track, settings.EnableLrclib, settings.EnableKugou)
		if err != nil {
			s.logger.Warn("failed to fetch lyrics from providers", "track_id", track.ID, "error", err)
			if lyric == nil {
				lyric = s.lyricsService.ResolveLyrics(ctx, track.ID, true)
				s.emitLyrics(track.ID, lyric)
			}
		} else if fetched != nil {
			s.emitLyrics(track.ID, fetched)
			return
		} else if lyric == nil {
			// No provider results — fall back to metadata if available.
			lyric = s.lyricsService.ResolveLyrics(ctx, track.ID, true)
			s.emitLyrics(track.ID, lyric)
		}
	} else if lyric == nil {
		// Providers disabled — fall back to metadata if available.
		lyric = s.lyricsService.ResolveLyrics(ctx, track.ID, true)
		s.emitLyrics(track.ID, lyric)
	}
}

func (s *PlayerService) emitLyrics(trackID string, lyric *domain.Lyric) {
	s.mu.RLock()
	currentID := ""
	if s.currentTrack != nil {
		currentID = s.currentTrack.ID
	}
	s.mu.RUnlock()

	if currentID != trackID {
		return
	}

	a := application.Get()
	if a == nil || a.Event == nil {
		return
	}
	a.Event.Emit("player:lyrics", lyric)
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

func (s *PlayerService) checkThreshold() {
	s.mu.Lock()
	track := s.currentTrack
	if track == nil {
		s.mu.Unlock()
		return
	}

	status := s.player.GetStatus()
	// Stale status or not yet updated by native player
	if status.TrackID != track.ID {
		s.mu.Unlock()
		return
	}

	// Impossible position guard: position should not significantly exceed elapsed time since start.
	// trackStartTime is adjusted on Seek, so this only blocks actual stale jumps from the engine.
	elapsed := time.Since(s.trackStartTime).Seconds()
	if !s.playCounted[track.ID] && status.Position > elapsed+5.0 {
		s.mu.Unlock()
		return
	}

	// Confirm position reset (native player is reporting 0 or near-start)
	if status.Position < 2.0 {
		s.posConfirmed[track.ID] = true
	}

	// Restart detection for same track (e.g. Repeat One)
	if status.Position < 1.0 && s.playCounted[track.ID] {
		s.playCounted[track.ID] = false
		s.posConfirmed[track.ID] = true
		s.trackStartTime = time.Now()
	}

	if status.PlaybackState != domain.PlaybackStatePlaying || !s.posConfirmed[track.ID] {
		s.mu.Unlock()
		return
	}

	// Threshold 1: Now Playing (3 seconds)
	if !s.npReported[track.ID] && status.Position >= 3.0 {
		s.npReported[track.ID] = true
		listeners := make([]func(*domain.TrackDTO), len(s.npListeners))
		copy(listeners, s.npListeners)
		s.mu.Unlock()

		for _, f := range listeners {
			f(track)
		}

		s.mu.Lock()
		// Re-lock to continue with scrobble logic
	}

	if s.playCounted[track.ID] {
		s.mu.Unlock()
		return
	}

	// Threshold 2: Scrobble (50% or 4 minutes)
	shouldScrobble := false
	if track.Duration >= 30 {
		if status.Position >= float64(track.Duration)/2 || status.Position >= 240 {
			shouldScrobble = true
		}
	}

	if shouldScrobble {
		s.playCounted[track.ID] = true
		delete(s.posConfirmed, track.ID)
		startTime := s.trackStartTime
		scrobbleListeners := make([]func(*domain.TrackDTO, time.Time), len(s.scrobbleListeners))
		copy(scrobbleListeners, s.scrobbleListeners)
		s.mu.Unlock()

		s.logger.Info("track playback threshold reached", "title", track.Title)

		// Increment local play count
		go func(id string) {
			if err := s.trackRepo.IncrementPlayCount(context.Background(), id); err != nil {
				s.logger.Warn("failed to increment play count", "track_id", id, "error", err)
			}
		}(track.ID)

		// Notify scrobble listeners (like Last.fm)
		for _, f := range scrobbleListeners {
			f(track, startTime)
		}
	} else {
		s.mu.Unlock()
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
				s.checkThreshold()

				if s.nowPlaying != nil && track != nil {
					status := s.player.GetStatus()
					s.nowPlaying.UpdateNowPlayingPosition(status.Position)
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

	s.mu.Lock()
	preQueued := s.nextPreQueued
	s.nextPreQueued = nil
	s.mu.Unlock()

	if preQueued != nil {
		// Advance queue index to match the pre-queued track.
		if next := s.queue.Next(); next == nil {
			// Queue exhausted — shouldn't happen if we peeked correctly, but handle it.
			s.mu.Lock()
			s.endedNaturally = true
			s.mu.Unlock()
			if err := s.Stop(); err != nil {
				s.logger.Error("failed to stop after queue end (gapless)", "error", err)
			}
			return
		}

		// For non-auto-transition players (miniaudio), start the pre-loaded sound now.
		if gp, ok := s.player.(domain.GaplessPlayer); ok {
			if err := gp.StartPreloaded(preQueued); err != nil {
				s.logger.Error("gapless start failed, falling back to hard load", "error", err)
				if err2 := s.loadAndPlay(preQueued); err2 != nil {
					s.logger.Error("fallback loadAndPlay failed", "error", err2)
				}
				return
			}
		}

		s.transitionToTrack(preQueued)

		// Pre-enqueue the next-next track.
		if nextNext := s.queue.PeekNext(); nextNext != nil {
			if gp, ok := s.player.(domain.GaplessPlayer); ok {
				if err := gp.EnqueueNext(nextNext); err != nil {
					s.logger.Warn("failed to pre-enqueue next track", "error", err)
				} else {
					s.mu.Lock()
					s.nextPreQueued = nextNext
					s.mu.Unlock()
				}
			}
		}
		return
	}

	// Standard path.
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

	activeQueue := s.queue.GetQueue()
	activeIDs := make([]string, len(activeQueue))
	for i, t := range activeQueue {
		activeIDs[i] = t.ID
	}

	originalQueue := s.queue.GetOriginalQueue()
	originalIDs := make([]string, len(originalQueue))
	for i, t := range originalQueue {
		originalIDs[i] = t.ID
	}

	status := s.player.GetStatus()

	currentID := ""
	if ct != nil {
		currentID = ct.ID
	}

	state := &domain.PlayerState{
		QueueTrackIDs:    activeIDs,
		OriginalTrackIDs: originalIDs,
		CurrentTrackID:   currentID,
		Position:         status.Position,
		Volume:           status.Volume,
		Muted:            status.Muted,
		Shuffle:          s.queue.GetShuffle(),
		RepeatMode:       s.queue.GetRepeatMode(),
	}
	if err := s.stateRepo.Save(ctx, state); err != nil {
		s.logger.Error("failed to save player state", "error", err)
	}
}

func (s *PlayerService) restoreState(ctx context.Context) {
	state, err := s.stateRepo.Load(ctx)
	if err != nil {
		s.logger.Error("failed to load player state, using defaults", "error", err)
		// Fallback to minimal default state
		state = &domain.PlayerState{
			Volume: 1.0,
			Muted:  false,
		}
	}
	if state == nil {
		return
	}

	loadTracks := func(ids []string) []*domain.TrackDTO {
		var tracks []*domain.TrackDTO
		for _, id := range ids {
			track, err := s.trackRepo.GetByID(ctx, id)
			if err != nil || track == nil {
				continue
			}
			if _, err := os.Stat(track.Path); err != nil {
				continue
			}
			tracks = append(tracks, track)
		}
		return tracks
	}

	activeTracks := loadTracks(state.QueueTrackIDs)
	originalTracks := loadTracks(state.OriginalTrackIDs)

	// If we have active tracks but no original tracks (e.g. state from older version),
	// treat active as original.
	if len(originalTracks) == 0 && len(activeTracks) > 0 {
		originalTracks = activeTracks
	}

	if len(activeTracks) > 0 {
		currentIndex := 0
		var currentTrack *domain.TrackDTO
		for i, t := range activeTracks {
			if t.ID == state.CurrentTrackID {
				currentIndex = i
				currentTrack = t
				break
			}
		}

		s.queue.Restore(originalTracks, activeTracks, currentIndex, state.Shuffle, state.RepeatMode)

		if currentTrack != nil {
			if err := s.player.Load(currentTrack); err != nil {
				s.logger.Error("failed to load track on restore", "track", currentTrack.Path, "error", err)
			} else {
				if err := s.player.Seek(state.Position); err != nil {
					s.logger.Warn("failed to seek to saved position on restore", "error", err)
				}
				s.mu.Lock()
				s.currentTrack = currentTrack
				s.mu.Unlock()

				go s.extractAndEmitPalette(currentTrack)
				go s.fetchAndEmitLyrics(currentTrack)
			}
		}
	}

	// Always attempt to restore these, even if no track is loaded
	if err := s.player.SetVolume(state.Volume); err != nil {
		s.logger.Warn("failed to restore volume", "error", err)
	}
	if err := s.player.SetMuted(state.Muted); err != nil {
		s.logger.Warn("failed to restore mute state", "error", err)
	}

	s.emitStatus()
}
