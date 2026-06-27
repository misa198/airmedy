package domain

import "time"

// Track represents a music track in the library
type Track struct {
	ID                  string    `json:"id" db:"id"`
	Path                string    `json:"path" db:"path"`
	Title               string    `json:"title" db:"title"`
	SortTitle           string    `json:"sort_title" db:"sort_title"`
	AlbumID             string    `json:"album_id" db:"album_id"`
	Year                int       `json:"year" db:"year"`
	TrackNumber         int       `json:"track_number" db:"track_number"`
	TotalTracks         int       `json:"total_tracks" db:"total_tracks"`
	DiscNumber          int       `json:"disc_number" db:"disc_number"`
	TotalDiscs          int       `json:"total_discs" db:"total_discs"`
	Duration            int       `json:"duration" db:"duration"` // in seconds
	Bitrate             int       `json:"bitrate" db:"bitrate"`
	SampleRate          int       `json:"sample_rate" db:"sample_rate"`
	Format              string    `json:"format" db:"format"`
	ArtworkKey          string    `json:"artwork_key" db:"artwork_key"`
	RawArtistNames      string    `json:"raw_artist_names" db:"raw_artist_names"`
	RawAlbumArtistNames string    `json:"raw_album_artist_names" db:"raw_album_artist_names"`
	RawGenreNames       string    `json:"raw_genre_names" db:"raw_genre_names"`
	RawComposerNames    string    `json:"raw_composer_names" db:"raw_composer_names"`
	Copyright           string    `json:"copyright" db:"copyright"`
	BPM                 int       `json:"bpm" db:"bpm"`
	Label               string    `json:"label" db:"label"`
	ISRC                string    `json:"isrc" db:"isrc"`
	PlayCount           int       `json:"play_count" db:"play_count"`
	OtherMetadata       string    `json:"other_metadata" db:"other_metadata"`
	FileSize            int64     `json:"file_size" db:"file_size"`
	IsFavorite          bool      `json:"is_favorite" db:"is_favorite"`
	Mtime               time.Time `json:"mtime" db:"mtime"`
	CreatedAt           time.Time `json:"created_at" db:"created_at"`
	UpdatedAt           time.Time `json:"updated_at" db:"updated_at"`
}

// TrackDTO represents a track with its related entities populated for the frontend
type TrackDTO struct {
	Track
	Artists      []*Artist   `json:"artists,omitempty"`
	Album        *Album      `json:"album,omitempty"`
	AlbumArtists []*Artist   `json:"album_artists,omitempty"`
	Genres       []*Genre    `json:"genres,omitempty"`
	Composers    []*Composer `json:"composers,omitempty"`
}

// Album represents a music album
type Album struct {
	ID               string    `json:"id" db:"id"`
	Title            string    `json:"title" db:"title"`
	SortTitle        string    `json:"sort_title" db:"sort_title"`
	NormalizationKey string    `json:"normalization_key" db:"normalization_key"`
	Year             int       `json:"year" db:"year"`
	Copyright        string    `json:"copyright" db:"copyright"`
	ArtworkKey       string    `json:"artwork_key" db:"artwork_key"`
	CreatedAt        time.Time `json:"created_at" db:"created_at"`
	UpdatedAt        time.Time `json:"updated_at" db:"updated_at"`
}

// AlbumDTO represents an album with its related entities populated for the frontend
type AlbumDTO struct {
	Album
	Artists []*Artist `json:"artists,omitempty"`
}

// Artist artwork sources. Each is stored independently; the one shown is chosen
// at read time by ResolveArtworkKey (manual outranks the local/online pair).
const (
	ArtworkSourceOnline    = "online"     // fetched automatically from Deezer
	ArtworkSourceLocalFile = "local_file" // scanned artist.jpg/png from disk
	ArtworkSourceManual    = "manual"     // set explicitly by the user
)

// Artist represents a music artist
type Artist struct {
	ID               string  `json:"id" db:"id"`
	Name             string  `json:"name" db:"name"`
	SortName         string  `json:"sort_name" db:"sort_name"`
	NormalizationKey string  `json:"normalization_key" db:"normalization_key"`
	ArtworkKeyManual *string `json:"artwork_key_manual" db:"artwork_key_manual"`
	ArtworkKeyLocal  *string `json:"artwork_key_local" db:"artwork_key_local"`
	ArtworkKeyOnline *string `json:"artwork_key_online" db:"artwork_key_online"`
	// ArtworkKey is the resolved key to display, computed at read time (not stored).
	ArtworkKey string    `json:"artwork_key" db:"-"`
	CreatedAt  time.Time `json:"created_at" db:"created_at"`
	UpdatedAt  time.Time `json:"updated_at" db:"updated_at"`
}

// ResolveArtworkKey picks which stored artwork to show. Manual always wins. The
// online (Deezer) image is shown only when useOnline is true and not suppressed by
// preferLocal: when preferLocal is set, an existing local image takes precedence
// over online. When online is disabled it is never shown, even if already cached.
func (a *Artist) ResolveArtworkKey(useOnline, preferLocal bool) string {
	if a.ArtworkKeyManual != nil && *a.ArtworkKeyManual != "" {
		return *a.ArtworkKeyManual
	}
	local := ""
	if a.ArtworkKeyLocal != nil {
		local = *a.ArtworkKeyLocal
	}
	if useOnline && (!preferLocal || local == "") &&
		a.ArtworkKeyOnline != nil && *a.ArtworkKeyOnline != "" {
		return *a.ArtworkKeyOnline
	}
	return local
}

// ShouldFetchOnline reports whether an online (Deezer) image is wanted but not yet
// cached: i.e. online is enabled, no manual image exists, the local image does not
// suppress online (per preferLocal), and no online image is stored yet.
func (a *Artist) ShouldFetchOnline(useOnline, preferLocal bool) bool {
	if !useOnline {
		return false
	}
	if a.ArtworkKeyManual != nil && *a.ArtworkKeyManual != "" {
		return false
	}
	if preferLocal && a.ArtworkKeyLocal != nil && *a.ArtworkKeyLocal != "" {
		return false
	}
	return a.ArtworkKeyOnline == nil || *a.ArtworkKeyOnline == ""
}

// ArtworkKeyForSource returns the stored key for a given source ("" if unset).
func (a *Artist) ArtworkKeyForSource(source string) *string {
	switch source {
	case ArtworkSourceManual:
		return a.ArtworkKeyManual
	case ArtworkSourceLocalFile:
		return a.ArtworkKeyLocal
	case ArtworkSourceOnline:
		return a.ArtworkKeyOnline
	default:
		return nil
	}
}

// Genre represents a music genre
type Genre struct {
	ID               string `json:"id" db:"id"`
	Name             string `json:"name" db:"name"`
	NormalizationKey string `json:"normalization_key" db:"normalization_key"`
}

// Composer represents a music composer
type Composer struct {
	ID               string `json:"id" db:"id"`
	Name             string `json:"name" db:"name"`
	NormalizationKey string `json:"normalization_key" db:"normalization_key"`
}

// Playlist represents a music playlist
type Playlist struct {
	ID          string    `json:"id" db:"id"`
	Name        string    `json:"name" db:"name"`
	Description string    `json:"description" db:"description"`
	ArtworkKey  *string   `json:"artwork_key" db:"artwork_key"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time `json:"updated_at" db:"updated_at"`
}

// Lyric represents a music lyric
type Lyric struct {
	TrackID     string    `json:"track_id" db:"track_id"`
	Content     string    `json:"content" db:"content"`
	Source      string    `json:"source" db:"source"`
	MetaContent string    `json:"meta_content" db:"meta_content"`
	MetaSource  string    `json:"meta_source" db:"meta_source"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time `json:"updated_at" db:"updated_at"`
}

// LyricsSearchResult represents a single search result from a lyrics provider
type LyricsSearchResult struct {
	Provider   string `json:"provider"`
	ID         string `json:"id"`
	TrackName  string `json:"track_name"`
	ArtistName string `json:"artist_name"`
	AlbumName  string `json:"album_name"`
	Duration   int    `json:"duration"`
	Content    string `json:"content"`
	Source     string `json:"source"`
}

// SyncProgress represents the current progress of a library sync
type SyncProgress struct {
	Current int    `json:"current"`
	Total   int    `json:"total"`
	Path    string `json:"path"`
}

// WatchedFolder represents a directory being watched for music files
type WatchedFolder struct {
	ID        string    `json:"id" db:"id"`
	Path      string    `json:"path" db:"path"`
	CreatedAt time.Time `json:"created_at" db:"created_at"`
}

// PlayerState holds the playback state to persist across app restarts
type PlayerState struct {
	QueueTrackIDs    []string   `json:"queue_track_ids"`
	OriginalTrackIDs []string   `json:"original_track_ids"`
	CurrentTrackID   string     `json:"current_track_id"`
	Position         float64    `json:"position"`
	Volume           float64    `json:"volume"`
	Muted            bool       `json:"muted"`
	Shuffle          bool       `json:"shuffle"`
	RepeatMode       RepeatMode `json:"repeat_mode"`
}

// AppSettings holds general application settings
type AppSettings struct {
	Language                 string `json:"language"`
	Theme                    string `json:"theme"` // "system", "light", "dark"
	StartAtLogin             bool   `json:"start_at_login"`
	ShowTrayIcon             bool   `json:"show_tray_icon"`
	AutoCheckUpdate          bool   `json:"auto_check_update"`
	LastFmUsername           string `json:"lastfm_username"`
	EQEnabled                bool   `json:"eq_enabled"`
	EnableLrclib             bool   `json:"enable_lrclib"`
	EnableKugou              bool   `json:"enable_kugou"`
	PreferLocalLyrics        bool   `json:"prefer_local_lyrics"`
	LyricsFolderEnabled      bool   `json:"lyrics_folder_enabled"`
	LyricsFolderPath         string `json:"lyrics_folder_path"`
	LyricsSubfolderEnabled   bool   `json:"lyrics_subfolder_enabled"`
	LyricsSubfolderName      string `json:"lyrics_subfolder_name"`
	UseOnlineArtistArtwork   bool   `json:"use_online_artist_artwork"`
	PreferLocalArtistArtwork bool   `json:"prefer_local_artist_artwork"`
	LastScanVersion          string `json:"last_scan_version"`
	PreventSleepWhilePlaying bool   `json:"prevent_sleep_while_playing"`
	RemoteServerEnabled      bool   `json:"remote_server_enabled"`
	RemoteServerPort         int    `json:"remote_server_port"`
	RemoteServerPassword     string `json:"remote_server_password"`
	ShowPlayerIndicator      bool   `json:"show_player_indicator"`
}
