package remoteserver

import "airmedy/internal/domain"

const (
	TypeAuth            = "auth"
	TypePlay            = "play"
	TypePause           = "pause"
	TypeTogglePause     = "toggle_pause"
	TypeNext            = "next"
	TypePrev            = "prev"
	TypeSeek            = "seek"
	TypeSetVolume       = "set_volume"
	TypeSetMuted        = "set_muted"
	TypeSetShuffle      = "set_shuffle"
	TypeSetRepeat       = "set_repeat"
	TypePlayQueueIndex  = "play_queue_index"
	TypeRemoveFromQueue = "remove_from_queue"
	TypeReorderQueue    = "reorder_queue"

	TypeAuthRequired  = "auth_required"
	TypeAuthOk        = "auth_ok"
	TypeAuthFailed    = "auth_failed"
	TypeTrackMetadata = "track_metadata"
	TypePlayerState   = "player_state"
	TypeQueue         = "queue"
	TypeLyrics        = "lyrics"
	TypeError         = "error"
)

// InboundMessage is a message from remote client to server.
type InboundMessage struct {
	Type     string   `json:"type"`
	Password string   `json:"password,omitempty"`
	Token    string   `json:"token,omitempty"`
	Position float64  `json:"position,omitempty"`
	Volume   float64  `json:"volume,omitempty"`
	Muted    bool     `json:"muted,omitempty"`
	Enabled  bool     `json:"enabled,omitempty"`
	Mode     string   `json:"mode,omitempty"`
	Index    int      `json:"index,omitempty"`
	TrackID  string   `json:"track_id,omitempty"`
	TrackIDs []string `json:"track_ids,omitempty"`
}

type AuthRequired struct {
	Type string `json:"type"`
}

type AuthOkState struct {
	TrackMetadata domain.PlayerTrackMetadata `json:"track_metadata"`
	PlayerState   domain.RemotePlayerState   `json:"player_state"`
	Queue         []*domain.TrackDTO         `json:"queue"`
}

type AuthOk struct {
	Type  string      `json:"type"`
	Token string      `json:"token"`
	State AuthOkState `json:"state"`
}

type AuthFailed struct {
	Type   string `json:"type"`
	Reason string `json:"reason"`
}

type TrackMetadataMessage struct {
	Type string                    `json:"type"`
	Data domain.PlayerTrackMetadata `json:"data"`
}

type PlayerStateMessage struct {
	Type string                  `json:"type"`
	Data domain.RemotePlayerState `json:"data"`
}

type QueueMessage struct {
	Type string             `json:"type"`
	Data []*domain.TrackDTO `json:"data"`
}

type LyricsMessage struct {
	Type string       `json:"type"`
	Data *domain.Lyric `json:"data"`
}

type ErrorMessage struct {
	Type    string `json:"type"`
	Message string `json:"message"`
}
