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

func TestSplitNames(t *testing.T) {
	def := DefaultDelimiters() // [";", "\\", ","]
	onlyHard := []string{";", "\\"}
	tests := []struct {
		input      string
		delimiters []string
		expected   []string
	}{
		// Default delimiters: split on ; \ and , (comma is in the default set).
		{"Artist A; Artist B", def, []string{"Artist A", "Artist B"}},
		{"Artist A\\Artist B", def, []string{"Artist A", "Artist B"}},
		{"Artist A; Artist B\\Artist C", def, []string{"Artist A", "Artist B", "Artist C"}},
		{"Artist A, Artist B", def, []string{"Artist A", "Artist B"}},
		// Without comma configured it stays one name.
		{"Artist A, Artist B", onlyHard, []string{"Artist A, Artist B"}},
		// Keywords / other punctuation never split.
		{"Artist A feat. Artist B", def, []string{"Artist A feat. Artist B"}},
		{"Artist A & Artist B", def, []string{"Artist A & Artist B"}},
		{"AC/DC", def, []string{"AC/DC"}},
		{"Rhythm and Blues", def, []string{"Rhythm and Blues"}},
		// Trim + dedup (case-insensitive).
		{"Artist A ;  artist a ", def, []string{"Artist A"}},
		{" ; Artist B ; ", def, []string{"Artist B"}},
		// Custom delimiters.
		{"Artist A, Artist B", []string{","}, []string{"Artist A", "Artist B"}},
		{"A|B/C", []string{"|", "/"}, []string{"A", "B", "C"}},
		// Empty / whitespace input.
		{"", def, nil},
		{"   ", def, nil},
	}

	for _, tc := range tests {
		got := SplitNames(tc.input, tc.delimiters)
		if len(got) != len(tc.expected) {
			t.Errorf("SplitNames(%q, %v) = %v, expected %v", tc.input, tc.delimiters, got, tc.expected)
			continue
		}
		for i := range got {
			if got[i] != tc.expected[i] {
				t.Errorf("SplitNames(%q)[%d] = %q, expected %q", tc.input, i, got[i], tc.expected[i])
			}
		}
	}
}

func TestValidateDelimiters(t *testing.T) {
	valid := [][]string{{}, {";"}, {";", "\\"}, {",", "/", "|"}}
	for _, v := range valid {
		if err := ValidateDelimiters(v); err != nil {
			t.Errorf("ValidateDelimiters(%v) = %v, expected nil", v, err)
		}
	}

	invalid := [][]string{
		{""},             // empty entry
		{"  "},           // whitespace-only
		{";", ";"},       // duplicate
		{"toolongdelim"}, // exceeds max length
	}
	for _, v := range invalid {
		if err := ValidateDelimiters(v); err == nil {
			t.Errorf("ValidateDelimiters(%v) = nil, expected error", v)
		}
	}
}
