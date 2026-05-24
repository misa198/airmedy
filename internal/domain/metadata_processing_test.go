package domain

import (
	"testing"
)

func TestNormalizeSort(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"The Beatles", "Beatles"},
		{"A Night at the Opera", "Night at the Opera"},
		{"An Awesome Wave", "Awesome Wave"},
		{"Ánh Nắng", "Anh Nang"},
		{"...Baby One More Time", "Baby One More Time"},
		{"Track 2", "Track 0002"},
		{"Track 10", "Track 0010"},
		{"100 tracks", "0100 tracks"},
		{"你好", "你好"},
	}

	for _, tc := range tests {
		got := NormalizeSort(tc.input)
		if got != tc.expected {
			t.Errorf("NormalizeSort(%q) = %q, expected %q", tc.input, got, tc.expected)
		}
	}
}

func TestNormalizationKey(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"AREA21", "area21"},
		{"Area21", "area21"},
		{"  Artist Name  ", "artist name"},
		{"Ánh Nắng", "anh nang"},
		{"đường", "duong"},
	}

	for _, tc := range tests {
		got := NormalizationKey(tc.input)
		if got != tc.expected {
			t.Errorf("NormalizationKey(%q) = %q, expected %q", tc.input, got, tc.expected)
		}
	}
}

func TestSplitArtists(t *testing.T) {
	tests := []struct {
		input    string
		expected []string
	}{
		{"Artist A, Artist B", []string{"Artist A", "Artist B"}},
		{"Artist A feat. Artist B", []string{"Artist A", "Artist B"}},
		{"Artist A & Artist B | Artist C", []string{"Artist A", "Artist B", "Artist C"}},
		{"Artist A featuring Artist B with Artist C", []string{"Artist A", "Artist B", "Artist C"}},
		{"Artist A vs. Artist B", []string{"Artist A", "Artist B"}},
		{"Artist A and Artist B", []string{"Artist A", "Artist B"}},
		{"Artist A; Artist B | Artist C", []string{"Artist A", "Artist B", "Artist C"}},
		{"tlinh & Low G", []string{"tlinh", "Low G"}},
		{"tlinh&Low G", []string{"tlinh", "Low G"}},
		{"tlinh &Low G", []string{"tlinh", "Low G"}},
		{"tlinh& Low G", []string{"tlinh", "Low G"}},
		{"Artist A / Artist B", []string{"Artist A", "Artist B"}},
		{"Artist A/Artist B\\Artist C", []string{"Artist A/Artist B", "Artist C"}},
		{"W/N", []string{"W/N"}},
		{"AC/DC", []string{"AC/DC"}},
		{"Brand New", []string{"Brand New"}},
		{"Earth, Wind & Fire", []string{"Earth", "Wind", "Fire"}},
	}

	for _, tc := range tests {
		got := SplitArtists(tc.input)
		if len(got) != len(tc.expected) {
			t.Errorf("SplitArtists(%q) returned %d artists, expected %d", tc.input, len(got), len(tc.expected))
			continue
		}
		for i := range got {
			if got[i] != tc.expected[i] {
				t.Errorf("SplitArtists(%q)[%d] = %q, expected %q", tc.input, i, got[i], tc.expected[i])
			}
		}
	}
}
