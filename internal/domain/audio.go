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
	SetRemoteCallbacks(play, pause, next, previous func())
	UpdateNowPlaying(track *TrackDTO, position float64, artworkPath string)
	ClearNowPlaying()
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
