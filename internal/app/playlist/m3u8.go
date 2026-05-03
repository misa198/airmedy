package playlist

import (
	"bufio"
	"fmt"
	"os"
	"strconv"
	"strings"
)

// M3U8Entry represents a single track entry parsed from an M3U8 file.
type M3U8Entry struct {
	Path     string
	Title    string
	Artist   string
	Album    string
	Genre    string
	Duration int
}

// M3U8File holds the parsed result of an extended M3U8 playlist file.
type M3U8File struct {
	PlaylistName string
	Entries      []M3U8Entry
}

// ParseM3U8 reads and parses an extended M3U8 file encoded in UTF-8.
// The file must start with #EXTM3U; unknown directives are silently ignored.
func ParseM3U8(filePath string) (*M3U8File, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return nil, fmt.Errorf("open m3u8: %w", err)
	}
	defer func() { _ = f.Close() }()

	result := &M3U8File{}
	var pending M3U8Entry
	hasHeader := false

	scanner := bufio.NewScanner(f)
	// Allow longer lines (some paths can be very long)
	scanner.Buffer(make([]byte, 64*1024), 64*1024)

	for scanner.Scan() {
		line := strings.TrimRight(scanner.Text(), "\r")

		if line == "" {
			continue
		}

		if !hasHeader {
			if line == "#EXTM3U" {
				hasHeader = true
			} else {
				return nil, fmt.Errorf("not a valid M3U8 file: missing #EXTM3U header")
			}
			continue
		}

		switch {
		case strings.HasPrefix(line, "#PLAYLIST:"):
			result.PlaylistName = strings.TrimPrefix(line, "#PLAYLIST:")

		case strings.HasPrefix(line, "#EXTINF:"):
			rest := strings.TrimPrefix(line, "#EXTINF:")
			// Format: duration,display name
			if comma := strings.IndexByte(rest, ','); comma >= 0 {
				dur, _ := strconv.Atoi(rest[:comma])
				pending.Duration = dur
				display := rest[comma+1:]
				// Try to split "Artist - Title"
				if idx := strings.Index(display, " - "); idx >= 0 {
					if pending.Artist == "" {
						pending.Artist = display[:idx]
					}
					if pending.Title == "" {
						pending.Title = display[idx+3:]
					}
				} else if pending.Title == "" {
					pending.Title = display
				}
			}

		case strings.HasPrefix(line, "#EXTALB:"):
			pending.Album = strings.TrimPrefix(line, "#EXTALB:")

		case strings.HasPrefix(line, "#EXTART:"):
			pending.Artist = strings.TrimPrefix(line, "#EXTART:")

		case strings.HasPrefix(line, "#EXTGENRE:"):
			pending.Genre = strings.TrimPrefix(line, "#EXTGENRE:")

		case strings.HasPrefix(line, "#"):
			// Unknown directive – ignore

		default:
			// Non-comment line is a file path
			pending.Path = line
			result.Entries = append(result.Entries, pending)
			pending = M3U8Entry{}
		}
	}

	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("read m3u8: %w", err)
	}
	if !hasHeader {
		return nil, fmt.Errorf("not a valid M3U8 file: missing #EXTM3U header")
	}

	return result, nil
}
