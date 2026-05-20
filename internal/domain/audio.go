package domain

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

// PlayerStatus represents the full state of the playback engine for the UI
type PlayerStatus struct {
	TrackID       string        `json:"track_id"`
	PlaybackState PlaybackState `json:"playback_state"`
	Position      float64       `json:"position"` // Current position in seconds
	Duration      float64       `json:"duration"` // Total duration in seconds
	Volume        float64       `json:"volume"`   // 0.0 to 1.0
	Muted         bool          `json:"muted"`
	RepeatMode    RepeatMode    `json:"repeat_mode"`
	Shuffle       bool          `json:"shuffle"`
	Theme         *ThemeColors  `json:"theme"`
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

// EQController is an optional interface implemented by audio players that support EQ
type EQController interface {
	SetEQBand(index int, frequency, gain, bandwidth float64) error
	SetEQEnabled(enabled bool) error
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
