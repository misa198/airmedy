package lyrics

import (
	"os"
	"path/filepath"
	"strings"

	"airmedy/internal/domain"
)

// localReader reads sibling lyric files (.lrc preferred, then .txt) located next
// to the audio file and sharing its basename.
type localReader struct{}

// NewLocalLyricsReader returns a domain.LocalLyricsReader backed by the filesystem.
func NewLocalLyricsReader() domain.LocalLyricsReader {
	return &localReader{}
}

// localCandidates pairs a file extension with the source label emitted when matched.
// Order is the priority order: .lrc before .txt.
var localCandidates = []struct {
	ext    string
	source string
}{
	{".lrc", "local-lrc"},
	{".txt", "local-txt"},
}

func (r *localReader) Read(audioPath string) (string, string, bool) {
	if audioPath == "" {
		return "", "", false
	}

	dir := filepath.Dir(audioPath)
	base := strings.TrimSuffix(filepath.Base(audioPath), filepath.Ext(audioPath))

	for _, c := range localCandidates {
		path := filepath.Join(dir, base+c.ext)
		data, err := os.ReadFile(path)
		if err != nil {
			continue
		}
		content := strings.TrimSpace(strings.TrimPrefix(string(data), "\ufeff"))
		if content == "" {
			continue
		}
		return content, c.source, true
	}

	return "", "", false
}
