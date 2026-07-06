package lyrics

import (
	"os"
	"path/filepath"
	"testing"
)

func TestValidSubfolderName(t *testing.T) {
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
		if got := ValidSubfolderName(name); got != want {
			t.Errorf("ValidSubfolderName(%q) = %v, want %v", name, got, want)
		}
	}
}

func TestResolveSubdir_CaseInsensitive(t *testing.T) {
	parent := t.TempDir()
	// Actual folder on disk is "Lyrics"; user typed "lyrics".
	actual := filepath.Join(parent, "Lyrics")
	if err := os.MkdirAll(actual, 0o755); err != nil {
		t.Fatal(err)
	}

	got := ResolveSubdir(parent, "lyrics")
	if got != actual {
		t.Errorf("ResolveSubdir matched case-insensitively: got %q, want %q", got, actual)
	}

	// Exact match wins without scanning.
	if got := ResolveSubdir(parent, "Lyrics"); got != actual {
		t.Errorf("exact match: got %q, want %q", got, actual)
	}

	// No matching dir → falls back to exact join (non-existent).
	want := filepath.Join(parent, "nope")
	if got := ResolveSubdir(parent, "nope"); got != want {
		t.Errorf("fallback: got %q, want %q", got, want)
	}
}
