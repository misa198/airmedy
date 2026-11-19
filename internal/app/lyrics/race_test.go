package lyrics

import (
	"context"
	"io"
	"log/slog"
	"testing"
	"time"

	"airmedy/internal/domain"
)

type delayedProvider struct {
	name    string
	delay   time.Duration
	content string
}

func (p delayedProvider) Name() string { return p.name }
func (p delayedProvider) Search(context.Context, string, string, int) ([]*domain.LyricsSearchResult, error) {
	return nil, nil
}
func (p delayedProvider) Fetch(ctx context.Context, track *domain.TrackDTO) (*domain.Lyric, error) {
	select {
	case <-time.After(p.delay):
	case <-ctx.Done():
		return nil, ctx.Err()
	}
	if p.content == "" {
		return nil, nil
	}
	return &domain.Lyric{TrackID: track.ID, Content: p.content, Source: p.name}, nil
}

func raceService(providers ...domain.LyricsProvider) *LyricsService {
	return NewLyricsService(&stubRepo{}, slog.New(slog.NewTextHandler(io.Discard, nil)), providers, nil, &stubSettingsRepo{})
}

var allOn = domain.AllLyricsProviders

func TestLyricQuality(t *testing.T) {
	cases := map[string]int{
		"plain words":                       0,
		"[ti:x]\nplain":                     0,
		"[00:01.00]line":                    1,
		"[00:01.00]a<00:01.50> b<00:02.00>": 2,
		"[00:01.00]<00:01.00>a <00:01.50>b": 2,
	}
	for in, want := range cases {
		if got := lyricQuality(in); got != want {
			t.Errorf("lyricQuality(%q) = %d, want %d", in, got, want)
		}
	}
}

func TestFetchRace_PrefersSyncedOverFasterPlain(t *testing.T) {
	s := raceService(
		delayedProvider{name: "lrclib", delay: 0, content: "plain lyrics"},
		delayedProvider{name: "lrcred", delay: 50 * time.Millisecond, content: "[00:01.00]a<00:01.50>"},
	)
	l, err := s.FetchFromProviders(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t"}}, allOn)
	if err != nil || l == nil || l.Source != "lrcred" {
		t.Fatalf("lyric=%+v err=%v", l, err)
	}
}

func TestFetchRace_WordSyncedReturnsImmediately(t *testing.T) {
	s := raceService(
		delayedProvider{name: "lrcred", delay: 0, content: "[00:01.00]a<00:01.50>"},
		delayedProvider{name: "kugou", delay: 5 * time.Second, content: "[00:01.00]slow"},
	)
	start := time.Now()
	l, err := s.FetchFromProviders(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t"}}, allOn)
	if err != nil || l == nil || l.Source != "lrcred" {
		t.Fatalf("lyric=%+v err=%v", l, err)
	}
	if time.Since(start) > time.Second {
		t.Fatalf("word-synced result should not wait for slower providers")
	}
}

func TestFetchRace_GracePeriodBoundsWait(t *testing.T) {
	old := raceGracePeriod
	raceGracePeriod = 50 * time.Millisecond
	t.Cleanup(func() { raceGracePeriod = old })
	s := raceService(
		delayedProvider{name: "lrclib", delay: 0, content: "[00:01.00]line"},
		delayedProvider{name: "kugou", delay: 5 * time.Second, content: "[00:01.00]a<00:01.50>"},
	)
	start := time.Now()
	l, err := s.FetchFromProviders(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t"}}, allOn)
	if err != nil || l == nil || l.Source != "lrclib" {
		t.Fatalf("lyric=%+v err=%v", l, err)
	}
	if time.Since(start) > time.Second {
		t.Fatalf("grace period not honoured")
	}
}

func TestFetchRace_NoResults(t *testing.T) {
	s := raceService(delayedProvider{name: "lrclib"}, delayedProvider{name: "kugou"})
	l, err := s.FetchFromProviders(context.Background(), &domain.TrackDTO{Track: domain.Track{ID: "t"}}, allOn)
	if err != nil || l != nil {
		t.Fatalf("lyric=%+v err=%v", l, err)
	}
}
