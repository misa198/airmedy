package lyrics

import (
	"os"
	"path/filepath"
	"strings"

	"airmedy/internal/domain"
	"airmedy/internal/ttml"
)

// localReader reads sibling lyric files (.lrc preferred, then .txt) located next
// to the audio file and sharing its basename.
type localReader struct{}

// NewLocalLyricsReader returns a domain.LocalLyricsReader backed by the filesystem.
func NewLocalLyricsReader() domain.LocalLyricsReader {
	return &localReader{}
}

// localCandidates pairs a file extension with the source label emitted when matched.
// Order is the priority order: .lrc, then .ttml, then .txt. .lrc stays first
// because it is the format Airmedy writes when saving lyrics to disk.
var localCandidates = []struct {
	ext    string
	source string
}{
	{".lrc", "local-lrc"},
	{".ttml", "local-ttml"},
	{".txt", "local-txt"},
}

func (r *localReader) Read(audioPath string, extraDirs ...string) (string, string, bool) {
	if audioPath == "" {
		return "", "", false
	}

	dir := filepath.Dir(audioPath)
	base := strings.TrimSuffix(filepath.Base(audioPath), filepath.Ext(audioPath))

	// Sibling dir first (highest priority), then any extra dirs in order.
	dirs := []string{dir}
	for _, ed := range extraDirs {
		if ed == "" || ed == dir {
			continue
		}
		dirs = append(dirs, ed)
	}

	for _, d := range dirs {
		for _, c := range localCandidates {
			path := filepath.Join(d, base+c.ext)
			data, err := os.ReadFile(path)
			if err != nil {
				continue
			}
			content := strings.TrimSpace(strings.TrimPrefix(string(data), "\ufeff"))
			if content == "" {
				continue
			}
			if c.ext == ".ttml" {
				// TTML is converted to (enhanced) LRC so every consumer can render it.
				converted, _, convErr := ttml.ToLRC(content)
				if convErr != nil || strings.TrimSpace(converted) == "" {
					continue
				}
				content = converted
			}
			return content, c.source, true
		}
	}

	return "", "", false
}
