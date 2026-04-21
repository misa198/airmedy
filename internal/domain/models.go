package domain

import "time"

// Track represents a music track in the library
type Track struct {
	ID          string    `json:"id"`
	Path        string    `json:"path"`
	Title       string    `json:"title"`
	Artist      string    `json:"artist"`
	Album       string    `json:"album"`
	ArtistID    string    `json:"artist_id"`
	AlbumID     string    `json:"album_id"`
	Genre       string    `json:"genre"`
	Composer    string    `json:"composer"`
	Year        int       `json:"year"`
	TrackNumber int       `json:"track_number"`
	DiscNumber  int       `json:"disc_number"`
	Duration    int       `json:"duration"` // in seconds
	Bitrate     int       `json:"bitrate"`
	SampleRate  int       `json:"sample_rate"`
	Format      string    `json:"format"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

// Album represents a music album
type Album struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	Artist      string `json:"artist"`
	ArtistID    string `json:"artist_id"`
	Year        int    `json:"year"`
	ArtworkPath string `json:"artwork_path"`
}

// Artist represents a music artist
type Artist struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}
