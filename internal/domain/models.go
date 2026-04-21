package domain

import "time"

// Track represents a music track in the library
type Track struct {
	ID                   string    `json:"id" db:"id"`
	Path                 string    `json:"path" db:"path"`
	Title                string    `json:"title" db:"title"`
	SortTitle            string    `json:"sort_title" db:"sort_title"`
	ArtistID             string    `json:"artist_id" db:"artist_id"`
	ArtistName           string    `json:"artist_name" db:"artist_name"`
	SortArtistName       string    `json:"sort_artist_name" db:"sort_artist_name"`
	AlbumID              string    `json:"album_id" db:"album_id"`
	AlbumName            string    `json:"album_name" db:"album_name"`
	SortAlbumName        string    `json:"sort_album_name" db:"sort_album_name"`
	AlbumArtistID        string    `json:"album_artist_id" db:"album_artist_id"`
	AlbumArtistName      string    `json:"album_artist_name" db:"album_artist_name"`
	SortAlbumArtistName  string    `json:"sort_album_artist_name" db:"sort_album_artist_name"`
	GenreID              string    `json:"genre_id" db:"genre_id"`
	GenreName            string    `json:"genre_name" db:"genre_name"`
	ComposerID           string    `json:"composer_id" db:"composer_id"`
	ComposerName         string    `json:"composer_name" db:"composer_name"`
	Year                 int       `json:"year" db:"year"`
	TrackNumber          int       `json:"track_number" db:"track_number"`
	TotalTracks          int       `json:"total_tracks" db:"total_tracks"`
	DiscNumber           int       `json:"disc_number" db:"disc_number"`
	TotalDiscs           int       `json:"total_discs" db:"total_discs"`
	Duration             int       `json:"duration" db:"duration"` // in seconds
	Bitrate              int       `json:"bitrate" db:"bitrate"`
	SampleRate           int       `json:"sample_rate" db:"sample_rate"`
	Format               string    `json:"format" db:"format"`
	ArtworkKey           string    `json:"artwork_key" db:"artwork_key"`
	CreatedAt            time.Time `json:"created_at" db:"created_at"`
	UpdatedAt            time.Time `json:"updated_at" db:"updated_at"`
}

// Album represents a music album
type Album struct {
	ID          string    `json:"id" db:"id"`
	Title       string    `json:"title" db:"title"`
	SortTitle   string    `json:"sort_title" db:"sort_title"`
	ArtistID    string    `json:"artist_id" db:"artist_id"`
	ArtistName  string    `json:"artist_name" db:"artist_name"`
	Year        int       `json:"year" db:"year"`
	ArtworkKey  string    `json:"artwork_key" db:"artwork_key"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time `json:"updated_at" db:"updated_at"`
}

// Artist represents a music artist
type Artist struct {
	ID        string    `json:"id" db:"id"`
	Name      string    `json:"name" db:"name"`
	SortName  string    `json:"sort_name" db:"sort_name"`
	CreatedAt time.Time `json:"created_at" db:"created_at"`
	UpdatedAt time.Time `json:"updated_at" db:"updated_at"`
}

// Genre represents a music genre
type Genre struct {
	ID   string `json:"id" db:"id"`
	Name string `json:"name" db:"name"`
}

// Composer represents a music composer
type Composer struct {
	ID   string `json:"id" db:"id"`
	Name string `json:"name" db:"name"`
}

// Playlist represents a music playlist
type Playlist struct {
	ID          string    `json:"id" db:"id"`
	Name        string    `json:"name" db:"name"`
	Description string    `json:"description" db:"description"`
	CreatedAt   time.Time `json:"created_at" db:"created_at"`
	UpdatedAt   time.Time `json:"updated_at" db:"updated_at"`
}

// Lyric represents a music lyric
type Lyric struct {
	TrackID   string    `json:"track_id" db:"track_id"`
	Content   string    `json:"content" db:"content"`
	Source    string    `json:"source" db:"source"`
	CreatedAt time.Time `json:"created_at" db:"created_at"`
	UpdatedAt time.Time `json:"updated_at" db:"updated_at"`
}
