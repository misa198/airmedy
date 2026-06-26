package lyrics

import (
	"os"
	"path/filepath"
	"testing"
)

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("write %s: %v", path, err)
	}
}

func TestLocalReader_Read(t *testing.T) {
	r := NewLocalLyricsReader()

	t.Run("lrc only", func(t *testing.T) {
		dir := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(dir, "Song.lrc"), "[00:01.00]hello")
		content, source, found := r.Read(audio)
		if !found || source != "local-lrc" || content != "[00:01.00]hello" {
			t.Fatalf("got (%q,%q,%v)", content, source, found)
		}
	})

	t.Run("txt only", func(t *testing.T) {
		dir := t.TempDir()
		audio := filepath.Join(dir, "Song.flac")
		writeFile(t, filepath.Join(dir, "Song.txt"), "plain lyrics")
		content, source, found := r.Read(audio)
		if !found || source != "local-txt" || content != "plain lyrics" {
			t.Fatalf("got (%q,%q,%v)", content, source, found)
		}
	})

	t.Run("lrc beats txt", func(t *testing.T) {
		dir := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(dir, "Song.lrc"), "[00:01.00]synced")
		writeFile(t, filepath.Join(dir, "Song.txt"), "plain")
		_, source, found := r.Read(audio)
		if !found || source != "local-lrc" {
			t.Fatalf("expected local-lrc, got %q (%v)", source, found)
		}
	})

	t.Run("none", func(t *testing.T) {
		dir := t.TempDir()
		_, _, found := r.Read(filepath.Join(dir, "Song.mp3"))
		if found {
			t.Fatal("expected not found")
		}
	})

	t.Run("empty file skipped", func(t *testing.T) {
		dir := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(dir, "Song.lrc"), "   \n  ")
		_, _, found := r.Read(audio)
		if found {
			t.Fatal("empty/whitespace file should not match")
		}
	})

	t.Run("name mismatch", func(t *testing.T) {
		dir := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(dir, "Other.lrc"), "[00:01.00]nope")
		_, _, found := r.Read(audio)
		if found {
			t.Fatal("mismatched basename should not match")
		}
	})

	t.Run("empty path", func(t *testing.T) {
		if _, _, found := r.Read(""); found {
			t.Fatal("empty path should not match")
		}
	})

	t.Run("extra dir match by basename", func(t *testing.T) {
		dir := t.TempDir()
		extra := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(extra, "Song.lrc"), "[00:01.00]from extra")
		content, source, found := r.Read(audio, extra)
		if !found || source != "local-lrc" || content != "[00:01.00]from extra" {
			t.Fatalf("got (%q,%q,%v)", content, source, found)
		}
	})

	t.Run("sibling beats extra dir", func(t *testing.T) {
		dir := t.TempDir()
		extra := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(dir, "Song.lrc"), "[00:01.00]sibling")
		writeFile(t, filepath.Join(extra, "Song.lrc"), "[00:01.00]extra")
		content, _, found := r.Read(audio, extra)
		if !found || content != "[00:01.00]sibling" {
			t.Fatalf("expected sibling to win, got %q (%v)", content, found)
		}
	})

	t.Run("empty extra dir ignored", func(t *testing.T) {
		dir := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(dir, "Song.lrc"), "[00:01.00]sibling")
		content, _, found := r.Read(audio, "")
		if !found || content != "[00:01.00]sibling" {
			t.Fatalf("got %q (%v)", content, found)
		}
	})

	t.Run("strips BOM", func(t *testing.T) {
		dir := t.TempDir()
		audio := filepath.Join(dir, "Song.mp3")
		writeFile(t, filepath.Join(dir, "Song.lrc"), "\ufeff[00:01.00]hi")
		content, _, found := r.Read(audio)
		if !found || content != "[00:01.00]hi" {
			t.Fatalf("BOM not stripped: %q (%v)", content, found)
		}
	})
}
