package metadata

import (
	"testing"

	"changeme/internal/domain"
)

func TestCleanSortString(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"The Beatles", "Beatles"},
		{"A Night at the Opera", "Night at the Opera"},
		{"An Awesome Wave", "Awesome Wave"},
		{"Normal Title", "Normal Title"},
	}

	for _, tc := range tests {
		got := cleanSortString(tc.input)
		if got != tc.expected {
			t.Errorf("cleanSortString(%q) = %q, expected %q", tc.input, got, tc.expected)
		}
	}
}

func TestApplySortFallbacks(t *testing.T) {
	track := &domain.Track{
		Title:      "The Title",
		ArtistName: "The Artist",
		AlbumName:  "The Album",
	}

	applySortFallbacks(track)

	if track.SortTitle != "Title" {
		t.Errorf("Expected SortTitle 'Title', got %q", track.SortTitle)
	}
	if track.SortArtistName != "Artist" {
		t.Errorf("Expected SortArtistName 'Artist', got %q", track.SortArtistName)
	}
	if track.SortAlbumName != "Album" {
		t.Errorf("Expected SortAlbumName 'Album', got %q", track.SortAlbumName)
	}
	if track.AlbumArtistName != "The Artist" {
		t.Errorf("Expected AlbumArtistName 'The Artist', got %q", track.AlbumArtistName)
	}
	if track.SortAlbumArtistName != "Artist" {
		t.Errorf("Expected SortAlbumArtistName 'Artist', got %q", track.SortAlbumArtistName)
	}
}
