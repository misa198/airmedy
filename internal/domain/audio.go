package domain

import "context"

// PlaybackState represents the current state of the audio player
type PlaybackState string

const (
	PlaybackStatePlaying PlaybackState = "playing"
	PlaybackStatePaused  PlaybackState = "paused"
	PlaybackStateStopped PlaybackState = "stopped"
)

// RepeatMode represents the repeat behavior of the player
type RepeatMode string

const (
	RepeatModeOff RepeatMode = "off"
	RepeatModeOne RepeatMode = "one"
	RepeatModeAll RepeatMode = "all"
)

// PlayerTrackMetadata contains static metadata that only changes on track switch.
// Broadcast to remote clients on track changes and theme extraction.
type PlayerTrackMetadata struct {
	TrackID  string       `json:"track_id"`
	Duration float64      `json:"duration"`
	Theme    *ThemeColors `json:"theme"`
}

// RemotePlayerState contains dynamic playback state for remote clients.
// Broadcast on explicit user interactions (play, pause, seek, volume, etc.).
type RemotePlayerState struct {
	PlaybackState PlaybackState `json:"playback_state"`
	Position      float64       `json:"position"`
	Volume        float64       `json:"volume"`
	Muted         bool          `json:"muted"`
	RepeatMode    RepeatMode    `json:"repeat_mode"`
	Shuffle       bool          `json:"shuffle"`
}

// PlayerStatus represents the full state of the playback engine for the UI
type PlayerStatus struct {
	TrackID         string        `json:"track_id"`
	LyricsRequestID uint64        `json:"lyrics_request_id"`
	PlaybackState   PlaybackState `json:"playback_state"`
	Position        float64       `json:"position"` // Current position in seconds
	Duration        float64       `json:"duration"` // Total duration in seconds
	Volume          float64       `json:"volume"`   // 0.0 to 1.0
	Muted           bool          `json:"muted"`
	RepeatMode      RepeatMode    `json:"repeat_mode"`
	Shuffle         bool          `json:"shuffle"`
	Theme           *ThemeColors  `json:"theme"`
}

// LyricsEvent is the lifecycle update for the current track's lyric request.
// RequestID lets clients discard results from an older load of the same track.
type LyricsEvent struct {
	TrackID   string `json:"track_id"`
	RequestID uint64 `json:"request_id"`
	State     string `json:"state"` // loading, ready, or error
	Lyric     *Lyric `json:"lyric,omitempty"`
}

// ThemeColors holds extracted palette data from the current track's artwork
type ThemeColors struct {
	Vibrant  string `json:"vibrant"`  // hex e.g. "#E11D48" — highest saturation cluster
	Muted    string `json:"muted"`    // hex — lowest saturation cluster
	Dominant string `json:"dominant"` // hex — largest pixel-count cluster
}

// NowPlayingController is an optional interface implemented by platform players
// that support OS-level Now Playing info and media key remote commands.
type NowPlayingController interface {
	SetupRemoteCommands()
	SetRemoteCallbacks(play, pause, next, previous func(), seek func(float64))
	UpdateNowPlaying(track *TrackDTO, position float64, artworkPath string)
	UpdateNowPlayingPosition(position float64)
	ClearNowPlaying()
}

// NowPlayingPlaybackState is an optional companion to NowPlayingController for
// platforms where the play/pause state must be pushed explicitly (e.g. Windows
// SMTC). macOS derives this from its own audio engine and does not implement it.
type NowPlayingPlaybackState interface {
	// SetNowPlayingPlaybackState updates the OS Now Playing playing/paused glyph.
	SetNowPlayingPlaybackState(playing bool)
}

// TrackTransitionNotifier is an optional platform integration used when
// playback automatically advances to another track. Implementations must not
// block or alter playback when the operating system declines notifications.
type TrackTransitionNotifier interface {
	NotifyTrackAdvanced(title, body, artworkPath string)
}

// TrackTransitionNotificationActivator registers the action invoked when a
// user clicks a platform track-transition notification.
type TrackTransitionNotificationActivator interface {
	SetTrackTransitionActivationCallback(callback func())
}

// EQBand represents a single frequency band in the equalizer
type EQBand struct {
	Index     int     `json:"index" db:"band_index"`
	Frequency float64 `json:"frequency" db:"frequency"`
	Gain      float64 `json:"gain" db:"gain"`           // in dB, -12 to +12
	Bandwidth float64 `json:"bandwidth" db:"bandwidth"` // Q factor
}

// EQProfile represents a named equalizer preset
type EQProfile struct {
	ID        string   `json:"id" db:"id"`
	Key       string   `json:"key" db:"preset_key"` // stable identifier for built-in presets; empty for user profiles
	Name      string   `json:"name" db:"name"`
	IsActive  bool     `json:"is_active" db:"is_active"`
	IsDefault bool     `json:"is_default" db:"is_default"`
	Bands     []EQBand `json:"bands"`
}

// GaplessPlayer is an optional interface for audio players that support gapless
// or near-gapless pre-loading of the next track.
type GaplessPlayer interface {
	// EnqueueNext pre-loads or pre-queues the next track while the current one plays.
	EnqueueNext(track *TrackDTO) error
	// StartPreloaded promotes the pre-loaded track to the active decoder and begins
	// playback. For auto-transition players (SFBAudioEngine) this is a no-op for audio
	// but must still update internal status fields to reflect the new track.
	StartPreloaded(track *TrackDTO) error
	// AutoTransitions returns true when the engine transitions to the queued track
	// on its own (e.g. SFBAudioEngine). The app layer must NOT call Load/Play on
	// HandleTrackEnd when this returns true.
	AutoTransitions() bool
	// ClearEnqueued discards the pending pre-queued track from the engine without
	// affecting the currently playing track.
	ClearEnqueued()
}

// CrossfadePlayer is an optional interface for audio players that can overlap
// the current track with the pre-loaded next track under a volume ramp.
type CrossfadePlayer interface {
	GaplessPlayer
	// SetCrossfadeDuration sets the fade length in seconds. Zero disables
	// crossfade and restores pure gapless behavior (including where
	// EnqueueNext pre-loads the next track to).
	SetCrossfadeDuration(seconds float64)
	// BeginCrossfadeToPreloaded starts the pre-loaded track overlapped with
	// the current one, ramping current→0 and preloaded→full over durationSec.
	// preampGainDB is the incoming track's normalization gain, applied
	// per-source. Updates player status to the new track.
	BeginCrossfadeToPreloaded(track *TrackDTO, durationSec, preampGainDB float64) error
	// FinishCrossfade force-completes any in-progress fade: the outgoing
	// source is stopped and unloaded, the incoming source snaps to full
	// level, and the idle slot becomes available. No-op when not fading.
	FinishCrossfade()
}

// EQController is an optional interface implemented by audio players that support EQ
type EQController interface {
	SetEQBand(index int, frequency, gain, bandwidth float64) error
	SetEQEnabled(enabled bool) error
}

// EQPreampController is an optional interface implemented by audio players that support a
// global user-adjustable EQ preamp (dB). It composes with NormalizationController's automatic
// loudness-normalization gain, but EQService applies 0 dB whenever EQ is disabled.
type EQPreampController interface {
	SetEQPreamp(db float64) error
}

// StereoWidthController is an optional interface implemented by audio players that support a
// global stereo-width (mid/side) adjustment. EQService applies 100 (neutral/identity) whenever
// EQ is disabled; 0 is mono, up to 200 is wider.
type StereoWidthController interface {
	SetStereoWidth(widthPercent float64) error
}

// NormalizationController is an optional interface implemented by audio players that can
// apply a pre-amp gain (dB) ahead of user volume, used for volume normalization.
type NormalizationController interface {
	SetPreampGain(db float64) error
}

// LoudnessAnalyzer runs one-time DSP analysis on an audio file, returning loudness,
// dynamics, and spectral features. Implemented by the in-process ffmpeg adapter.
type LoudnessAnalyzer interface {
	Analyze(ctx context.Context, path string) (*TrackFeatures, error)
}

// AnalysisComponents identifies independently versioned raw-analysis sources.
// A track can be current for one source while still needing the other.
type AnalysisComponents uint8

const (
	AnalysisComponentFFmpeg AnalysisComponents = 1 << iota
	AnalysisComponentAubio
	AnalysisComponentsAll = AnalysisComponentFFmpeg | AnalysisComponentAubio
)

// ComponentAnalyzer is implemented by analyzers that can skip stale sources
// while sharing one decode when more than one source is requested.
type ComponentAnalyzer interface {
	AnalyzeComponents(ctx context.Context, path string, components AnalysisComponents) (*TrackFeatures, error)
}

// SleepInhibitor prevents the OS from sleeping while music is playing.
type SleepInhibitor interface {
	Inhibit() error
	Release() error
}

// AudioPlayer is the interface for platform-native audio playback engines
type AudioPlayer interface {
	// Control operations
	Play() error
	Pause() error
	Stop() error
	Seek(position float64) error
	SetVolume(volume float64) error
	SetMuted(muted bool) error

	// Lifecycle
	Load(track *TrackDTO) error
	Unload() error

	// Queries
	GetStatus() PlayerStatus

	// Callbacks
	OnTrackEnd(callback func())
}
