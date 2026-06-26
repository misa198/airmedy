package player

import (
	"os"
	"path/filepath"
	"testing"
)

func TestValidLyricsSubfolderName(t *testing.T) {
	cases := map[string]bool{
		"lyrics":  true,
		"Lyrics":  true,
		"my lrc":  true,
		"":        false,
		"  ":      false,
		".":       false,
		"..":      false,
		"a/b":     false,
		`a\b`:     false,
		"../evil": false,
		"ok..bad": false,
	}
	for name, want := range cases {
		if got := validLyricsSubfolderName(name); got != want {
			t.Errorf("validLyricsSubfolderName(%q) = %v, want %v", name, got, want)
		}
	}
}

func TestResolveLyricsSubdir_CaseInsensitive(t *testing.T) {
	parent := t.TempDir()
	// Actual folder on disk is "Lyrics"; user typed "lyrics".
	actual := filepath.Join(parent, "Lyrics")
	if err := os.MkdirAll(actual, 0o755); err != nil {
		t.Fatal(err)
	}

	got := resolveLyricsSubdir(parent, "lyrics")
	if got != actual {
		t.Errorf("resolveLyricsSubdir matched case-insensitively: got %q, want %q", got, actual)
	}

	// Exact match wins without scanning.
	if got := resolveLyricsSubdir(parent, "Lyrics"); got != actual {
		t.Errorf("exact match: got %q, want %q", got, actual)
	}

	// No matching dir → falls back to exact join (non-existent).
	want := filepath.Join(parent, "nope")
	if got := resolveLyricsSubdir(parent, "nope"); got != want {
		t.Errorf("fallback: got %q, want %q", got, want)
	}
}
