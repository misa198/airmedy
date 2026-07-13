package player

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"math/rand"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"airmedy/internal/app/lyrics"
	"airmedy/internal/app/normalization"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"

	"github.com/wailsapp/wails/v3/pkg/application"
	"go.uber.org/fx"
)

// PlayerService coordinates playback and queue management.
type PlayerService struct {
	mu                              sync.RWMutex
	player                          domain.AudioPlayer
	queue                           *QueueService
	logger                          *slog.Logger
	artworkCache                    domain.ArtworkCache
	lyricsService                   *lyrics.LyricsService
	nowPlaying                      domain.NowPlayingController // nil on non-darwin or when unsupported
	currentTrack                    *domain.TrackDTO
	currentTheme                    *domain.ThemeColors
	trackRepo                       domain.TrackRepository
	stateRepo                       domain.PlayerStateRepository
	settingsRepo                    domain.SettingsRepository
	normSvc                         *normalization.NormalizationService
	notifier                        domain.TrackTransitionNotifier
	autoAdvanceNotificationsEnabled bool

	trackStartTime time.Time
	playCounted    map[string]bool // trackID -> bool
	npReported     map[string]bool // trackID -> bool
	posConfirmed   map[string]bool // trackID -> bool

	tickerMu     sync.Mutex
	tickerCancel context.CancelFunc
	tickInterval time.Duration

	endedNaturally bool             // true when queue ran out; cleared on Play or loadAndPlay
	nextPreQueued  *domain.TrackDTO // track pre-enqueued for gapless transition

	crossfadeSec float64 // crossfade duration in seconds; 0 = off (gapless)
	fading       bool    // a crossfade overlap is currently in progress
	fadeGen      int     // bumped on every fade begin/snap; voids stale completion timers

	sleepInhibitor domain.SleepInhibitor

	// emitStatusHook overrides event emission in tests (nil in production).
	emitStatusHook func()

	trackLoadListeners []func(*domain.TrackDTO)

	statusListeners           []func(domain.PlayerStatus)
	trackMetadataListeners    []func(domain.PlayerTrackMetadata)
	remoteStateListeners      []func(domain.RemotePlayerState)
	queueListeners            []func([]*domain.TrackDTO)
	scrobbleListeners         []func(*domain.TrackDTO, time.Time)
	npListeners               []func(*domain.TrackDTO)
	lyricsListeners           []func(*domain.Lyric)
	artworkCrossfadeListeners []func(domain.ArtworkCrossfadeEvent)
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
	sleepInhibitor domain.SleepInhibitor,
	normSvc *normalization.NormalizationService,
	notifier domain.TrackTransitionNotifier,
	lc fx.Lifecycle,
) *PlayerService {
	s := &PlayerService{
		player:                          player,
		queue:                           queue,
		logger:                          logger,
		artworkCache:                    artworkCache,
		lyricsService:                   lyricsService,
		trackRepo:                       trackRepo,
		stateRepo:                       stateRepo,
		settingsRepo:                    settingsRepo,
		sleepInhibitor:                  sleepInhibitor,
		normSvc:                         normSvc,
		notifier:                        notifier,
		autoAdvanceNotificationsEnabled: true,
		tickInterval:                    500 * time.Millisecond,
		playCounted:                     make(map[string]bool),
		npReported:                      make(map[string]bool),
		posConfirmed:                    make(map[string]bool),
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

// AddTrackLoadListener registers a callback fired whenever a track is loaded into the player.
func (s *PlayerService) AddTrackLoadListener(f func(*domain.TrackDTO)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.trackLoadListeners = append(s.trackLoadListeners, f)
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

// AddLyricsListener registers a callback that will be called whenever lyrics are resolved for the current track.
func (s *PlayerService) AddLyricsListener(f func(*domain.Lyric)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.lyricsListeners = append(s.lyricsListeners, f)
}

// AddTrackMetadataListener registers a callback invoked on track switches and theme updates.
func (s *PlayerService) AddTrackMetadataListener(f func(domain.PlayerTrackMetadata)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.trackMetadataListeners = append(s.trackMetadataListeners, f)
}

// AddRemoteStateListener registers a callback invoked on explicit playback state changes.
func (s *PlayerService) AddRemoteStateListener(f func(domain.RemotePlayerState)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.remoteStateListeners = append(s.remoteStateListeners, f)
}

// GetTrackMetadata returns current track metadata for remote clients.
func (s *PlayerService) GetTrackMetadata() domain.PlayerTrackMetadata {
	s.mu.RLock()
	theme := s.currentTheme
	s.mu.RUnlock()
	status := s.player.GetStatus()
	return domain.PlayerTrackMetadata{
		TrackID:  status.TrackID,
		Duration: status.Duration,
		Theme:    theme,
	}
}

// GetRemotePlayerState returns current dynamic state for remote clients.
func (s *PlayerService) GetRemotePlayerState() domain.RemotePlayerState {
	status := s.player.GetStatus()
	return domain.RemotePlayerState{
		PlaybackState: status.PlaybackState,
		Position:      status.Position,
		Volume:        status.Volume,
		Muted:         status.Muted,
		RepeatMode:    s.queue.GetRepeatMode(),
		Shuffle:       s.queue.GetShuffle(),
	}
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

	// No track loaded but the queue has tracks (e.g. added via "Add to queue"
	// without ever pressing play): start playback from the queue instead of
	// silently resuming a player with nothing loaded.
	if ct == nil {
		s.queue.SetShuffle(false)
		if track := s.queue.GetCurrentTrack(); track != nil {
			return s.loadAndPlay(track)
		}
		return s.PlayQueueIndex(0)
	}

	// Track ended naturally (queue ran out): SFBAudioEngine won't restart a finished
	// item via Play() alone — reload from the beginning.
	if ended {
		return s.loadAndPlay(ct)
	}

	err := s.player.Play()
	if err == nil {
		s.startPositionTicker()
		// Prime the pre-queued next track if it isn't already. A session-restored
		// track is Loaded but never pre-enqueued, so without this a plain resume
		// leaves nextPreQueued == nil and the first auto-advance skips crossfade.
		s.mu.RLock()
		hasNext := s.nextPreQueued != nil
		s.mu.RUnlock()
		if !hasNext {
			s.preEnqueueNext()
		}
		// Ensure the OS Now Playing card is populated before flipping the glyph.
		// After an app restart the track is restored (loaded) but never pushed to
		// the OS controls, so a plain resume would otherwise toggle play state on a
		// card that was never created and nothing would show.
		s.pushNowPlaying(ct, s.player.GetStatus().Position)
		s.setNowPlayingPlaybackState(true)
		s.emitStatus()
		s.emitRemoteState()
	}
	return err
}

// Pause pauses playback.
func (s *PlayerService) Pause() error {
	// Snap an in-flight crossfade so resume is a plain single-source resume.
	s.finishActiveCrossfade()
	err := s.player.Pause()
	if err == nil {
		s.stopPositionTicker()
		s.setNowPlayingPlaybackState(false)
		s.emitStatus()
		s.emitRemoteState()
		s.saveState(context.Background())
	}
	return err
}

// setNowPlayingPlaybackState pushes the play/pause glyph to the OS Now Playing
// controls on platforms that require it explicitly (Windows SMTC). No-op on
// platforms whose player does not implement domain.NowPlayingPlaybackState (macOS).
func (s *PlayerService) setNowPlayingPlaybackState(playing bool) {
	if sps, ok := s.nowPlaying.(domain.NowPlayingPlaybackState); ok {
		sps.SetNowPlayingPlaybackState(playing)
	}
}

// SetNowPlayingActivateCallback registers a callback that is invoked when the
// OS media session requests app activation (e.g. the user clicks "Now Playing"
// in the Windows SMTC flyout). The callback should bring the appropriate window
// to front. No-op on platforms whose backend does not support it.
func (s *PlayerService) SetNowPlayingActivateCallback(cb func()) {
	type activatable interface{ SetActivateCallback(func()) }
	if s.nowPlaying != nil {
		if a, ok := s.nowPlaying.(activatable); ok {
			a.SetActivateCallback(cb)
		}
	}
}

// Stop stops playback.
func (s *PlayerService) Stop() error {
	s.snapActiveCrossfade()
	err := s.player.Stop()
	if err == nil {
		s.stopPositionTicker()
		if s.nowPlaying != nil {
			s.nowPlaying.ClearNowPlaying()
		}
		s.emitStatus()
		s.emitRemoteState()
		s.saveState(context.Background())
	}
	return err
}

// Next plays the next track in the queue.
func (s *PlayerService) Next() error {
	track := s.queue.Next()
	if track == nil {
		// Queue exhausted (repeat off). Mark ended so a subsequent Play() reloads
		// the current track instead of issuing a plain Play() on a stopped engine,
		// which SFBAudioEngine won't restart. Mirrors HandleTrackEnd.
		s.mu.Lock()
		s.endedNaturally = true
		s.mu.Unlock()
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
	// Snap an in-flight crossfade first so the seek targets the incoming track.
	s.finishActiveCrossfade()
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
		s.emitRemoteState()
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
		s.emitRemoteState()
	}
	return err
}

// SetMuted mutes or unmutes playback.
func (s *PlayerService) SetMuted(muted bool) error {
	err := s.player.SetMuted(muted)
	if err == nil {
		s.emitStatus()
		s.emitRemoteState()
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

// ReplaceQueueKeepingCurrentTrackIDs replaces the queue while leaving the
// currently loaded track playing at its current position. It is intended for
// queue sources (such as Mood Radio) that use the current track as their seed.
func (s *PlayerService) ReplaceQueueKeepingCurrentTrackIDs(ctx context.Context, trackIDs []string) error {
	tracks, err := s.trackRepo.GetByIDs(ctx, trackIDs)
	if err != nil {
		return fmt.Errorf("failed to fetch tracks by ids: %w", err)
	}
	return s.ReplaceQueueKeepingCurrentTrack(tracks)
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
	s.emitQueue()
	track := s.queue.GetCurrentTrack()
	if track == nil {
		return nil
	}
	return s.loadAndPlay(track)
}

// ReplaceQueueKeepingCurrentTrack replaces the queue and points it at the
// active track without reloading the audio engine. The active track must occur
// in tracks; callers should use PlayTracks when they intend to start a track.
func (s *PlayerService) ReplaceQueueKeepingCurrentTrack(tracks []*domain.TrackDTO) error {
	s.mu.RLock()
	currentTrack := s.currentTrack
	s.mu.RUnlock()
	if currentTrack == nil {
		return fmt.Errorf("cannot replace queue while no track is loaded")
	}

	currentIndex := -1
	for i, track := range tracks {
		if track != nil && track.ID == currentTrack.ID {
			currentIndex = i
			break
		}
	}
	if currentIndex == -1 {
		return fmt.Errorf("current track %q is not in replacement queue", currentTrack.ID)
	}

	s.queue.SetQueue(tracks, currentIndex)
	// The next track changed, so discard any previous gapless preload without
	// disturbing the active audio source and preload the new successor.
	s.resyncPreQueue()
	s.emitQueue()
	s.emitStatus()
	s.emitRemoteState()
	s.saveState(context.Background())
	return nil
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
	// Toggling shuffle rebuilds the play order, so whatever was cached as
	// the "next" track before the toggle is very likely wrong now.
	s.resyncPreQueue()
	s.emitStatus()
	s.emitRemoteState()
	s.emitQueueOrder()
	return nil
}

// SetMaxQueueSize applies a live queue-size cap change (e.g. a settings save)
// to the running queue and notifies the frontend if it trimmed anything.
func (s *PlayerService) SetMaxQueueSize(n int) {
	s.queue.SetMaxSize(n)
	s.emitQueue()
}

// SetCrossfadeSeconds applies a live crossfade duration change. The pre-queue
// is re-synced because switching 0↔N changes where EnqueueNext pre-loads the
// next track to on macOS (engine queue vs. second deck). An in-flight fade
// completes with its captured duration.
func (s *PlayerService) SetCrossfadeSeconds(n int) {
	sec := float64(domain.ClampCrossfadeSeconds(n))

	s.mu.Lock()
	if s.crossfadeSec == sec {
		s.mu.Unlock()
		return
	}
	s.crossfadeSec = sec
	s.mu.Unlock()

	if cp, ok := s.player.(domain.CrossfadePlayer); ok {
		cp.SetCrossfadeDuration(sec)
	}

	s.resyncPreQueue()
}

// SetAutoAdvanceNotificationsEnabled applies the persisted notification
// preference without interrupting playback.
func (s *PlayerService) SetAutoAdvanceNotificationsEnabled(enabled bool) {
	s.mu.Lock()
	s.autoAdvanceNotificationsEnabled = enabled
	s.mu.Unlock()
}

// SetRepeatMode sets the repeat mode.
func (s *PlayerService) SetRepeatMode(mode domain.RepeatMode) error {
	s.queue.SetRepeatMode(mode)

	// Re-sync the gapless pre-queue with the new repeat mode so that stale
	// pre-queued tracks don't cause the wrong track to play on the next track-end.
	s.resyncPreQueue()

	s.emitStatus()
	s.emitRemoteState()
	return nil
}

// PlayNext inserts a track immediately after the currently playing track.
func (s *PlayerService) PlayNext(track *domain.TrackDTO) {
	s.queue.InsertAfterCurrent(track)
	// Inserting right after the current track changes what plays next, so
	// any cached pre-queued track from before this insert is stale.
	s.resyncPreQueue()
	s.emitQueue()
}

// PlayNextTracks inserts a list of tracks immediately after the currently playing track.
func (s *PlayerService) PlayNextTracks(tracks []*domain.TrackDTO) {
	s.queue.InsertListAfterCurrent(tracks)
	s.resyncPreQueue()
	s.emitQueue()
}

// AppendTracks adds tracks to the end of the queue in a single mutation.
func (s *PlayerService) AppendTracks(tracks []*domain.TrackDTO) {
	s.queue.AppendTracks(tracks)
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
	} else {
		// The removed track may have been the cached pre-queued "next"
		// track — if so, that cache now points at a track no longer in
		// the queue and must be recomputed.
		s.resyncPreQueue()
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

	// Reordering can change which track comes after the currently-playing
	// one, so the cached nextPreQueued (captured before the reorder) may
	// point at a now-stale track.
	s.resyncPreQueue()

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

// ReapplyNormalization recomputes and pushes the pre-amp gain for the
// currently loaded track. Called after normalization settings change so
// toggling enabled/mode/target takes effect immediately during playback.
func (s *PlayerService) ReapplyNormalization() {
	s.mu.RLock()
	track := s.currentTrack
	s.mu.RUnlock()
	if track == nil || s.normSvc == nil {
		return
	}
	s.normSvc.ApplyToPlayer(context.Background(), track, s.queue.PeekNext())
}

// Internal helpers

func (s *PlayerService) loadAndPlay(track *domain.TrackDTO) error {
	// A hard load supersedes any in-flight crossfade.
	s.snapActiveCrossfade()
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

	if s.normSvc != nil {
		s.normSvc.ApplyToPlayer(context.Background(), track, s.queue.PeekNext())
	}

	s.mu.RLock()
	loadListeners := append([]func(*domain.TrackDTO){}, s.trackLoadListeners...)
	s.mu.RUnlock()
	for _, f := range loadListeners {
		f(track)
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
	s.emitTrackMetadata()
	s.emitRemoteState()

	s.pushNowPlaying(track, 0)

	go s.extractAndEmitPalette(track)
	go s.fetchAndEmitLyrics(track)

	s.saveState(context.Background())

	// Pre-enqueue the next track for gapless transitions.
	if gapless {
		s.preEnqueueNext()
	}

	return nil
}

// pushNowPlaying sends the current track to the OS Now Playing panel, falling
// back to the file name when the track has no title tag (mirroring the UI).
func (s *PlayerService) pushNowPlaying(track *domain.TrackDTO, position float64) {
	if s.nowPlaying == nil {
		return
	}
	artworkPath := ""
	if track.ArtworkKey != "" && s.artworkCache.Exists(track.ArtworkKey) {
		artworkPath = s.artworkCache.GetPath(track.ArtworkKey)
	}
	npTrack := *track
	if npTrack.Title == "" {
		npTrack.Title = fallbackTitle(track.Path)
	}
	s.nowPlaying.UpdateNowPlaying(&npTrack, position, artworkPath)
}

// fallbackTitle derives a display title from a file path: base name without extension.
func fallbackTitle(path string) string {
	base := filepath.Base(path)
	return strings.TrimSuffix(base, filepath.Ext(base))
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

	if s.normSvc != nil {
		s.normSvc.ApplyToPlayer(context.Background(), track, s.queue.PeekNext())
	}

	s.startPositionTicker()
	s.emitStatus()
	s.emitTrackMetadata()
	s.emitRemoteState()

	s.pushNowPlaying(track, 0)

	go s.extractAndEmitPalette(track)
	go s.fetchAndEmitLyrics(track)

	s.saveState(context.Background())
}

// notifyTrackAutoAdvanced sends a best-effort OS notification only for an
// automatic move to a different track. It intentionally does not share the
// generic load path because that path is also used by manual navigation.
func (s *PlayerService) notifyTrackAutoAdvanced(track *domain.TrackDTO, previousTrackID string) {
	if track == nil || s.notifier == nil {
		return
	}

	s.mu.RLock()
	enabled := s.autoAdvanceNotificationsEnabled
	s.mu.RUnlock()
	if !enabled || (previousTrackID != "" && previousTrackID == track.ID) {
		return
	}

	title := track.Title
	if title == "" {
		title = fallbackTitle(track.Path)
	}
	artist := track.RawArtistNames
	if len(track.Artists) > 0 {
		names := make([]string, 0, len(track.Artists))
		for _, a := range track.Artists {
			if a != nil && a.Name != "" {
				names = append(names, a.Name)
			}
		}
		if len(names) > 0 {
			artist = strings.Join(names, ", ")
		}
	}
	album := ""
	if track.Album != nil {
		album = track.Album.Title
	}
	artworkPath := ""
	if track.ArtworkKey != "" {
		artworkPath = s.artworkCache.GetPath(track.ArtworkKey)
	}
	s.notifier.NotifyTrackAdvanced(title, fmt.Sprintf("%s - %s", artist, album), artworkPath)
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

	s.emitTrackMetadata()

	app := application.Get()
	if app != nil && app.Event != nil {
		defer func() { _ = recover() }()
		app.Event.Emit("player:theme", colors)
	}
}

// lyricsResolveParams builds the preference + extra lyric dirs used by both the
// emit-on-track-change path and the pull-based GetCurrentLyrics. Extra dirs are
// in priority order (sibling dir is always checked first by the reader):
// subfolder next to the track, then the global lyrics folder.
func (s *PlayerService) lyricsResolveParams(ctx context.Context, track *domain.TrackDTO) (preferLocal bool, extraDirs []string) {
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		settings = &domain.AppSettings{}
	}
	preferLocal = settings.PreferLocalLyrics
	if settings.LyricsSubfolderEnabled && lyrics.ValidSubfolderName(settings.LyricsSubfolderName) {
		extraDirs = append(extraDirs, lyrics.ResolveSubdir(filepath.Dir(track.Path), settings.LyricsSubfolderName))
	}
	if settings.LyricsFolderEnabled && settings.LyricsFolderPath != "" {
		extraDirs = append(extraDirs, settings.LyricsFolderPath)
	}
	return preferLocal, extraDirs
}

// GetCurrentLyrics resolves the best available lyric for the currently loaded
// track (sibling file, embedded metadata tag, or cached provider content),
// honoring the user's local-vs-provider preference. Used by the frontend on
// startup to recover lyrics for a restored track, since the restore-time
// player:lyrics emit happens before the frontend's listener is registered.
func (s *PlayerService) GetCurrentLyrics() *domain.Lyric {
	if s.lyricsService == nil {
		return nil
	}
	s.mu.RLock()
	track := s.currentTrack
	s.mu.RUnlock()
	if track == nil {
		return nil
	}
	ctx := context.Background()
	preferLocal, extraDirs := s.lyricsResolveParams(ctx, track)
	return s.lyricsService.ResolveLyrics(ctx, track.ID, track.Path, preferLocal, extraDirs...)
}

func (s *PlayerService) fetchAndEmitLyrics(track *domain.TrackDTO) {
	if s.lyricsService == nil {
		return
	}
	ctx := context.Background()

	preferLocal, extraDirs := s.lyricsResolveParams(ctx, track)

	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		settings = &domain.AppSettings{}
	}

	// 1. Emit the best currently-available lyric for the chosen preference.
	//    hasLocal comes from the SAME read, so the step-2 guard can't disagree
	//    with what was just emitted due to a transient second-read failure.
	lyric, hasLocal := s.lyricsService.ResolveWithLocal(ctx, track.ID, track.Path, preferLocal, extraDirs...)

	// Check if we will fetch online lyrics.
	dbLyric, _ := s.lyricsService.GetLyrics(ctx, track.ID)
	hasExternal := dbLyric != nil && dbLyric.Content != ""
	anyProviderEnabled := settings.EnableLrclib || settings.EnableKugou
	willFetch := (!preferLocal || !hasLocal) && !hasExternal && anyProviderEnabled

	// Only emit immediately if we are not going to fetch, or if the lyric
	// we have is already the preferred type (e.g. we prefer local, or we have cached external).
	// If we will fetch and prefer online lyrics, we hold off emitting the local fallback
	// lyric to avoid a visual flash on the UI.
	if lyric != nil && (!willFetch || preferLocal) {
		s.emitLyrics(track.ID, lyric)
	}

	// 2. When local lyrics are preferred and present, they win outright. Don't
	//    fetch providers, so nothing overrides the displayed local lyric.
	if preferLocal && hasLocal {
		return
	}

	// 3. Fetch from providers when enabled and not already cached.
	if willFetch {
		if _, err := s.lyricsService.FetchFromProviders(ctx, track, settings.EnableLrclib, settings.EnableKugou); err != nil {
			s.logger.Warn("failed to fetch lyrics from providers", "track_id", track.ID, "error", err)
		}
	}

	// 4. Re-resolve so the final emit honors the preference now that provider
	//    content may be cached. This is the single source of priority truth.
	resolved := s.lyricsService.ResolveLyrics(ctx, track.ID, track.Path, preferLocal, extraDirs...)
	s.emitLyrics(track.ID, resolved)
}

func (s *PlayerService) emitLyrics(trackID string, lyric *domain.Lyric) {
	s.mu.RLock()
	currentID := ""
	if s.currentTrack != nil {
		currentID = s.currentTrack.ID
	}
	listeners := make([]func(*domain.Lyric), len(s.lyricsListeners))
	copy(listeners, s.lyricsListeners)
	s.mu.RUnlock()

	if currentID != trackID {
		return
	}

	for _, f := range listeners {
		f(lyric)
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

func (s *PlayerService) emitTrackMetadata() {
	s.mu.RLock()
	theme := s.currentTheme
	listeners := make([]func(domain.PlayerTrackMetadata), len(s.trackMetadataListeners))
	copy(listeners, s.trackMetadataListeners)
	s.mu.RUnlock()

	status := s.player.GetStatus()
	meta := domain.PlayerTrackMetadata{
		TrackID:  status.TrackID,
		Duration: status.Duration,
		Theme:    theme,
	}
	for _, f := range listeners {
		f(meta)
	}
}

func (s *PlayerService) emitRemoteState() {
	status := s.player.GetStatus()
	s.mu.RLock()
	listeners := make([]func(domain.RemotePlayerState), len(s.remoteStateListeners))
	copy(listeners, s.remoteStateListeners)
	s.mu.RUnlock()

	state := domain.RemotePlayerState{
		PlaybackState: status.PlaybackState,
		Position:      status.Position,
		Volume:        status.Volume,
		Muted:         status.Muted,
		RepeatMode:    s.queue.GetRepeatMode(),
		Shuffle:       s.queue.GetShuffle(),
	}
	for _, f := range listeners {
		f(state)
	}
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

// emitQueueOrder notifies queue listeners and the frontend of a pure reorder
// (shuffle/unshuffle) where the track set is unchanged. In-process listeners
// (tray, remote server) still get the full queue since that costs nothing
// extra — only the Wails webview event, which must be JSON-serialized across
// the IPC boundary, is slimmed down to ids so the UI can remap its
// already-loaded TrackDTOs instead of re-transferring the full queue.
func (s *PlayerService) emitQueueOrder() {
	queue := s.queue.GetQueue()

	s.mu.RLock()
	listeners := make([]func([]*domain.TrackDTO), len(s.queueListeners))
	copy(listeners, s.queueListeners)
	s.mu.RUnlock()

	for _, f := range listeners {
		f(queue)
	}

	ids := make([]string, len(queue))
	for i, t := range queue {
		ids[i] = t.ID
	}

	app := application.Get()
	if app != nil && app.Event != nil {
		app.Event.Emit("player:queue-reordered", ids)
	}
}

func (s *PlayerService) checkThreshold(track *domain.TrackDTO, status domain.PlayerStatus) {
	s.mu.Lock()
	// Ensure we're still on the same track after potential lock wait
	if s.currentTrack == nil || s.currentTrack.ID != track.ID {
		s.mu.Unlock()
		return
	}

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

func (s *PlayerService) inhibitSleep() {
	if s.sleepInhibitor == nil || s.settingsRepo == nil {
		return
	}
	settings, err := s.settingsRepo.Load(context.Background())
	if err != nil {
		s.logger.Debug("sleep inhibitor: failed to load settings", "error", err)
		return
	}
	if !settings.PreventSleepWhilePlaying {
		s.logger.Debug("sleep inhibitor: disabled by setting, skipping")
		return
	}
	if err := s.sleepInhibitor.Inhibit(); err != nil {
		s.logger.Debug("sleep inhibitor: inhibit failed", "error", err)
		return
	}
	s.logger.Debug("sleep inhibitor: acquired")
}

func (s *PlayerService) releaseSleep() {
	if s.sleepInhibitor == nil {
		return
	}
	if err := s.sleepInhibitor.Release(); err != nil {
		s.logger.Debug("sleep inhibitor: release failed", "error", err)
		return
	}
	s.logger.Debug("sleep inhibitor: released")
}

func (s *PlayerService) startPositionTicker() {
	s.inhibitSleep()

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

				if track == nil {
					continue
				}

				status := s.player.GetStatus()
				s.checkThreshold(track, status)
				s.maybeStartCrossfade(status)

				if s.nowPlaying != nil {
					s.nowPlaying.UpdateNowPlayingPosition(status.Position)
				}
			}
		}
	}()
}

func (s *PlayerService) stopPositionTicker() {
	s.releaseSleep()

	s.tickerMu.Lock()
	defer s.tickerMu.Unlock()
	if s.tickerCancel != nil {
		s.tickerCancel()
		s.tickerCancel = nil
	}
}

// preEnqueueNext pre-loads the queue's next track into the player for a
// gapless or crossfade transition, recording it in nextPreQueued.
func (s *PlayerService) preEnqueueNext() {
	next := s.queue.PeekNext()
	if next == nil {
		return
	}
	gp, ok := s.player.(domain.GaplessPlayer)
	if !ok {
		return
	}
	if err := gp.EnqueueNext(next); err != nil {
		s.logger.Warn("failed to pre-enqueue next track", "error", err)
		return
	}
	s.mu.Lock()
	s.nextPreQueued = next
	s.mu.Unlock()
}

// resyncPreQueue clears a stale gapless pre-queue and recomputes it from the
// queue's current state. Any mutation that can change which track
// immediately follows the currently-playing one (reorder, insert-next,
// remove, shuffle toggle, repeat-mode change, crossfade-duration change)
// must call this — otherwise the cached nextPreQueued keeps pointing at the
// track that used to be next, and the wrong track plays at crossfade/track-end.
func (s *PlayerService) resyncPreQueue() {
	s.mu.Lock()
	hadPreQueued := s.nextPreQueued != nil
	s.nextPreQueued = nil
	s.mu.Unlock()

	if !hadPreQueued {
		return
	}

	if gp, ok := s.player.(domain.GaplessPlayer); ok {
		gp.ClearEnqueued()
	}
	s.preEnqueueNext()
}

// maybeStartCrossfade begins the crossfade into the pre-queued track when the
// current track is close enough to its end. Called from the position ticker.
func (s *PlayerService) maybeStartCrossfade(status domain.PlayerStatus) {
	cp, ok := s.player.(domain.CrossfadePlayer)
	if !ok {
		return
	}

	s.mu.RLock()
	sec := s.crossfadeSec
	fading := s.fading
	hasNext := s.nextPreQueued != nil
	s.mu.RUnlock()

	if sec <= 0 || fading || !hasNext {
		return
	}
	if status.PlaybackState != domain.PlaybackStatePlaying || status.Duration < 2 {
		return
	}
	effFade := math.Min(sec, status.Duration/2)
	remaining := status.Duration - status.Position
	// Below 0.4s remaining, let the normal end-callback/gapless path win
	// (covers a seek that landed on the last instant of the track).
	if remaining > effFade || remaining <= 0.4 {
		return
	}

	// Claim the fade before touching the queue so a concurrent manual skip
	// can't start a second overlap.
	s.mu.Lock()
	if s.fading || s.nextPreQueued == nil {
		s.mu.Unlock()
		return
	}
	next := s.nextPreQueued
	s.nextPreQueued = nil
	s.fading = true
	s.fadeGen++
	gen := s.fadeGen
	s.mu.Unlock()

	// Advance the queue index to the pre-queued track so transitionToTrack
	// and normalization see the correct PeekNext.
	if s.queue.Next() == nil {
		s.mu.Lock()
		s.fading = false
		s.mu.Unlock()
		return
	}

	if err := s.runCrossfade(cp, next, effFade, gen); err != nil {
		s.logger.Error("crossfade begin failed, falling back to hard load", "error", err)
		s.mu.Lock()
		s.fading = false
		s.mu.Unlock()
		if err2 := s.loadAndPlay(next); err2 != nil {
			s.logger.Error("fallback loadAndPlay failed", "error", err2)
		}
	}
}

// runCrossfade drives the native overlap for an already-claimed fade (fading
// set, queue index already on next) and schedules its completion.
func (s *PlayerService) runCrossfade(cp domain.CrossfadePlayer, next *domain.TrackDTO, effFade float64, gen int) error {
	gainDB := 0.0
	if s.normSvc != nil {
		if g, _, err := s.normSvc.ComputeGain(context.Background(), next, s.queue.PeekNext()); err == nil {
			gainDB = g
		}
	}

	if err := cp.BeginCrossfadeToPreloaded(next, effFade, gainDB); err != nil {
		return err
	}

	s.mu.RLock()
	current := s.currentTrack
	s.mu.RUnlock()
	if current != nil {
		s.emitArtworkCrossfade(domain.ArtworkCrossfadeEvent{
			TransitionID:   gen,
			Phase:          "start",
			FromArtworkKey: current.ArtworkKey,
			ToArtworkKey:   next.ArtworkKey,
			DurationMS:     int(math.Round(effFade * 1000)),
		})
	}

	s.mu.RLock()
	previousTrackID := ""
	if s.currentTrack != nil {
		previousTrackID = s.currentTrack.ID
	}
	s.mu.RUnlock()
	s.notifyTrackAutoAdvanced(next, previousTrackID)
	s.transitionToTrack(next)

	time.AfterFunc(time.Duration((effFade+0.3)*float64(time.Second)), func() {
		s.finishCrossfade(gen)
	})
	return nil
}

// snapCrossfade force-completes the in-progress fade with generation gen:
// the outgoing source is stopped and the incoming one snaps to full level.
// Returns false when that fade is stale or none is running.
func (s *PlayerService) snapCrossfade(gen int) bool {
	s.mu.Lock()
	if !s.fading || gen != s.fadeGen {
		s.mu.Unlock()
		return false
	}
	s.fading = false
	s.mu.Unlock()

	if cp, ok := s.player.(domain.CrossfadePlayer); ok {
		cp.FinishCrossfade()
	}
	s.emitArtworkCrossfade(domain.ArtworkCrossfadeEvent{TransitionID: gen, Phase: "end"})
	return true
}

// emitArtworkCrossfade keeps fullscreen artwork synchronized with the native
// overlap. It is deliberately separate from player:status so manual changes
// cannot be mistaken for a crossfade by the frontend.
func (s *PlayerService) emitArtworkCrossfade(event domain.ArtworkCrossfadeEvent) {
	for _, listener := range s.artworkCrossfadeListeners {
		listener(event)
	}
	app := application.Get()
	if app != nil && app.Event != nil {
		defer func() { _ = recover() }()
		app.Event.Emit("player:artwork-crossfade", event)
	}
}

// AddArtworkCrossfadeListener registers an observer for visual crossfade
// lifecycle events. It is used by unit tests and non-Wails adapters.
func (s *PlayerService) AddArtworkCrossfadeListener(listener func(domain.ArtworkCrossfadeEvent)) {
	s.artworkCrossfadeListeners = append(s.artworkCrossfadeListeners, listener)
}

// snapActiveCrossfade snaps whatever fade is currently running, without
// pre-enqueueing a follow-up track (the caller is about to replace it).
func (s *PlayerService) snapActiveCrossfade() {
	s.mu.RLock()
	fading := s.fading
	gen := s.fadeGen
	s.mu.RUnlock()
	if fading {
		s.snapCrossfade(gen)
	}
}

// finishCrossfade completes the fade and pre-enqueues the following track.
// Pre-enqueueing is deferred to here because the idle deck/slot is occupied
// by the outgoing source until the fade ends.
func (s *PlayerService) finishCrossfade(gen int) {
	if !s.snapCrossfade(gen) {
		return
	}
	s.preEnqueueNext()
}

// finishActiveCrossfade finishes whatever fade is currently running,
// including the follow-up pre-enqueue. No-op when not fading.
func (s *PlayerService) finishActiveCrossfade() {
	s.mu.RLock()
	fading := s.fading
	gen := s.fadeGen
	s.mu.RUnlock()
	if fading {
		s.finishCrossfade(gen)
	}
}

// HandleTrackEnd is called by the native player when a track finishes playing.
func (s *PlayerService) HandleTrackEnd() {
	// During a crossfade the outgoing source's end is already accounted for;
	// the native layers also guard this, but never double-advance the queue.
	s.mu.RLock()
	fading := s.fading
	s.mu.RUnlock()
	if fading {
		s.logger.Debug("track end during crossfade ignored")
		return
	}

	s.stopPositionTicker()
	s.logger.Debug("track ended, moving to next")

	s.mu.Lock()
	preQueued := s.nextPreQueued
	s.nextPreQueued = nil
	previousTrackID := ""
	if s.currentTrack != nil {
		previousTrackID = s.currentTrack.ID
	}
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
				} else {
					s.notifyTrackAutoAdvanced(preQueued, previousTrackID)
				}
				return
			}
		}

		s.notifyTrackAutoAdvanced(preQueued, previousTrackID)
		s.transitionToTrack(preQueued)

		// Pre-enqueue the next-next track.
		s.preEnqueueNext()
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
	} else {
		s.notifyTrackAutoAdvanced(track, previousTrackID)
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

	// Apply the configured queue cap before anything touches the queue —
	// regardless of whether a session was persisted — so it's never left at
	// the zero-value (unlimited) until the settings are next saved.
	if settings, err := s.settingsRepo.Load(ctx); err == nil {
		s.queue.SetMaxSize(domain.ResolveMaxQueueSize(settings.MaxQueueSize))
		s.SetCrossfadeSeconds(settings.CrossfadeSeconds)
		s.SetAutoAdvanceNotificationsEnabled(settings.AutoAdvanceNotificationsEnabled)
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

				if s.normSvc != nil {
					s.normSvc.ApplyToPlayer(ctx, currentTrack, s.queue.PeekNext())
				}

				s.pushNowPlaying(currentTrack, state.Position)

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
