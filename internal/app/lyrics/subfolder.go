package lyrics

import (
	"os"
	"path/filepath"
	"strings"
)

// ValidSubfolderName reports whether name is a safe single path segment for a
// lyrics subfolder next to a track. Rejects empty, path separators, and any
// traversal so the joined path can't escape the track's directory.
func ValidSubfolderName(name string) bool {
	name = strings.TrimSpace(name)
	if name == "" || name == "." || name == ".." {
		return false
	}
	if strings.ContainsAny(name, `/\`) || strings.Contains(name, "..") {
		return false
	}
	return true
}

// ResolveSubdir returns the path of the subfolder named `name` inside
// `parent`, matching case-insensitively. macOS/Windows filesystems are already
// case-insensitive; this makes Linux (case-sensitive) behave the same, so the
// name the user typed matches regardless of case. Falls back to the exact join
// if no directory matches (path may not exist yet).
func ResolveSubdir(parent, name string) string {
	entries, err := os.ReadDir(parent)
	if err == nil {
		var caseInsensitiveMatch string
		for _, e := range entries {
			if !e.IsDir() {
				continue
			}
			if e.Name() == name {
				return filepath.Join(parent, e.Name())
			}
			if caseInsensitiveMatch == "" && strings.EqualFold(e.Name(), name) {
				caseInsensitiveMatch = e.Name()
			}
		}
		if caseInsensitiveMatch != "" {
			return filepath.Join(parent, caseInsensitiveMatch)
		}
	}
	return filepath.Join(parent, name)
}
