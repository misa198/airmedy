package lyrics

import (
	"context"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"testing"
	"time"

	"airmedy/internal/domain"
)

type stubRepo struct {
	lyric *domain.Lyric
}

func (s *stubRepo) GetByTrackID(_ context.Context, _ string) (*domain.Lyric, error) {
	return s.lyric, nil
}
func (s *stubRepo) GetByTrackIDs(_ context.Context, ids []string) (map[string]*domain.Lyric, error) {
	result := make(map[string]*domain.Lyric)
	if s.lyric != nil {
		for _, id := range ids {
			result[id] = s.lyric
		}
	}
	return result, nil
}
func (s *stubRepo) Save(_ context.Context, _ *domain.Lyric) error   { return nil }
func (s *stubRepo) Upsert(_ context.Context, l *domain.Lyric) error { s.lyric = l; return nil }
func (s *stubRepo) Delete(_ context.Context, _ string) error        { return nil }

type stubLocal struct {
	content string
	source  string
}

type stubSettingsRepo struct{ settings *domain.AppSettings }

func (s *stubSettingsRepo) Save(_ context.Context, settings *domain.AppSettings) error {
	s.settings = settings
	return nil
}
func (s *stubSettingsRepo) Load(context.Context) (*domain.AppSettings, error) { return s.settings, nil }

type stubMobileSyncCache struct {
	entries map[string]*domain.MobileSyncLyricCache
}

func (s *stubMobileSyncCache) GetByTrackIDs(_ context.Context, ids []string) (map[string]*domain.MobileSyncLyricCache, error) {
	result := make(map[string]*domain.MobileSyncLyricCache)
	for _, id := range ids {
		if entry := s.entries[id]; entry != nil {
			copy := *entry
			result[id] = &copy
		}
	}
	return result, nil
}
func (s *stubMobileSyncCache) Upsert(_ context.Context, entry *domain.MobileSyncLyricCache) error {
	copy := *entry
	s.entries[entry.TrackID] = &copy
	return nil
}

func (s stubLocal) Read(string, ...string) (string, string, bool) {
	if s.content == "" {
		return "", "", false
	}
	return s.content, s.source, true
}

func newService(lyric *domain.Lyric, local domain.LocalLyricsReader) *LyricsService {
	return NewLyricsService(&stubRepo{lyric: lyric}, slog.New(slog.NewTextHandler(io.Discard, nil)), nil, local, nil)
}

func TestResolveLyrics(t *testing.T) {
	ctx := context.Background()

	cases := []struct {
		name        string
		dbLyric     *domain.Lyric
		local       domain.LocalLyricsReader
		preferLocal bool
		wantSource  string // "" => expect nil
	}{
		{
			name:        "local file beats tag and provider (preferLocal)",
			dbLyric:     &domain.Lyric{MetaContent: "tag", MetaSource: "meta-plain", Content: "prov", Source: "lrclib-synced"},
			local:       stubLocal{content: "[00:01.00]file", source: "local-lrc"},
			preferLocal: true,
			wantSource:  "local-lrc",
		},
		{
			name:        "tag used when no local file (preferLocal)",
			dbLyric:     &domain.Lyric{MetaContent: "tag", MetaSource: "meta-plain", Content: "prov", Source: "lrclib-synced"},
			local:       stubLocal{},
			preferLocal: true,
			wantSource:  "meta-plain",
		},
		{
			name:        "provider wins when preferLocal false",
			dbLyric:     &domain.Lyric{MetaContent: "tag", MetaSource: "meta-plain", Content: "prov", Source: "lrclib-synced"},
			local:       stubLocal{content: "file", source: "local-txt"},
			preferLocal: false,
			wantSource:  "lrclib-synced",
		},
		{
			name:        "fall back to local when provider empty and preferLocal false",
			dbLyric:     &domain.Lyric{MetaContent: "tag", MetaSource: "meta-plain"},
			local:       stubLocal{content: "file", source: "local-txt"},
			preferLocal: false,
			wantSource:  "local-txt",
		},
		{
			name:        "nil when nothing available",
			dbLyric:     nil,
			local:       stubLocal{},
			preferLocal: true,
			wantSource:  "",
		},
		{
			name:        "local file with no db row (preferLocal)",
			dbLyric:     nil,
			local:       stubLocal{content: "file", source: "local-lrc"},
			preferLocal: true,
			wantSource:  "local-lrc",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			svc := newService(tc.dbLyric, tc.local)
			got := svc.ResolveLyrics(ctx, "track1", "/music/Song.mp3", tc.preferLocal, "")
			if tc.wantSource == "" {
				if got != nil {
					t.Fatalf("expected nil, got %+v", got)
				}
				return
			}
			if got == nil {
				t.Fatalf("expected source %q, got nil", tc.wantSource)
			}
			if got.Source != tc.wantSource {
				t.Fatalf("expected source %q, got %q", tc.wantSource, got.Source)
			}
		})
	}
}

func TestResolveForMobileSyncCachesAndInvalidatesLocalLyrics(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "Song.mp3")
	lyricPath := filepath.Join(dir, "Song.lrc")
	if err := os.WriteFile(lyricPath, []byte("first"), 0o600); err != nil {
		t.Fatal(err)
	}
	service := NewLyricsService(&stubRepo{lyric: &domain.Lyric{TrackID: "track", Content: "provider", Source: "lrclib"}}, slog.New(slog.NewTextHandler(io.Discard, nil)), nil, nil, &stubSettingsRepo{settings: &domain.AppSettings{PreferLocalLyrics: true}})
	cache := &stubMobileSyncCache{entries: map[string]*domain.MobileSyncLyricCache{}}
	tracks := []*domain.TrackDTO{{Track: domain.Track{ID: "track", Path: path}}}

	first, err := service.ResolveForMobileSync(context.Background(), tracks, cache)
	if err != nil {
		t.Fatal(err)
	}
	if got := first["track"]; got == nil || got.Content != "first" || got.Source != "local-lrc" {
		t.Fatalf("unexpected first lyric: %+v", got)
	}
	firstVersion := cache.entries["track"].Version

	if err := os.WriteFile(lyricPath, []byte("second"), 0o600); err != nil {
		t.Fatal(err)
	}
	next := time.Now().Add(2 * time.Second)
	if err := os.Chtimes(lyricPath, next, next); err != nil {
		t.Fatal(err)
	}
	second, err := service.ResolveForMobileSync(context.Background(), tracks, cache)
	if err != nil {
		t.Fatal(err)
	}
	if got := second["track"]; got == nil || got.Content != "second" {
		t.Fatalf("unexpected updated lyric: %+v", got)
	}
	if cache.entries["track"].Version == firstVersion {
		t.Fatal("expected lyric version to change after local file edit")
	}
}
