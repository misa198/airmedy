package lyrics

import (
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"

	"airmedy/internal/domain"
)

type stubSettingsRepo struct {
	domain.SettingsRepository
	settings *domain.AppSettings
}

func (s *stubSettingsRepo) Load(context.Context) (*domain.AppSettings, error) { return s.settings, nil }

const lrcredModelTTML = `<tt xmlns="http://www.w3.org/ns/ttml" xmlns:lrc="http://lrc.red/lyric-ttml-internal" lrc:byModel="true"><body><div>
<p begin="1" end="2"><span begin="1" end="2">model</span></p></div></body></tt>`

const lrcredHumanTTML = `<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
<p begin="1" end="2"><span begin="1" end="2">human</span></p></div></body></tt>`

type fakeLrcred struct {
	mu       sync.Mutex
	ttml     string
	lrc      string
	paths    []string
	agents   []string
	searchQs []string
}

func (f *fakeLrcred) handler(w http.ResponseWriter, r *http.Request) {
	f.mu.Lock()
	f.paths = append(f.paths, r.URL.Path)
	f.agents = append(f.agents, r.Header.Get("User-Agent"))
	f.mu.Unlock()
	switch {
	case r.URL.Path == "/search.json":
		f.mu.Lock()
		f.searchQs = append(f.searchQs, r.URL.Query().Get("q"))
		f.mu.Unlock()
		_, _ = io.WriteString(w, `{"hits":[
			{"isrc":"XXAAA0000001","title":"Other Song","artist":"Nobody","duration":100},
			{"isrc":"GBUM71029604","title":"Bohemian Rhapsody","artist":"Queen","album":"A Night at the Opera","duration":356.52}
		],"next":null}`)
	case strings.HasSuffix(r.URL.Path, ".ttml") && f.ttml != "":
		_, _ = io.WriteString(w, f.ttml)
	case strings.HasSuffix(r.URL.Path, ".lrc") && f.lrc != "":
		_, _ = io.WriteString(w, f.lrc)
	default:
		http.NotFound(w, r)
	}
}

func newTestLrcred(t *testing.T, f *fakeLrcred, modelSyncs bool) *LrcredProvider {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	t.Cleanup(srv.Close)
	p := NewLrcredProvider(slog.New(slog.NewTextHandler(io.Discard, nil)),
		&stubSettingsRepo{settings: &domain.AppSettings{LrcredModelSyncs: modelSyncs}})
	p.baseURL = srv.URL
	p.client = srv.Client()
	return p
}

func TestLrcred_FetchByISRCUsesTTML(t *testing.T) {
	f := &fakeLrcred{ttml: lrcredHumanTTML, lrc: "[00:01.00]from lrc"}
	p := newTestLrcred(t, f, true)
	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Title: "x", ISRC: "gbum71029604"}}
	l, err := p.Fetch(context.Background(), track)
	if err != nil || l == nil {
		t.Fatalf("lyric=%v err=%v", l, err)
	}
	if l.Content != "[00:01.000]human<00:02.000>" || l.Source != "lrcred-synced" {
		t.Fatalf("unexpected lyric %+v", l)
	}
	if f.paths[0] != "/s/GBUM71029604.ttml" {
		t.Fatalf("expected ISRC lookup first, got %v", f.paths)
	}
	for _, ua := range f.agents {
		if !strings.HasPrefix(ua, "Airmedy/") || !strings.Contains(ua, "https://") {
			t.Fatalf("User-Agent must identify the app with a link, got %q", ua)
		}
	}
}

func TestLrcred_ModelSyncs(t *testing.T) {
	track := &domain.TrackDTO{Track: domain.Track{ID: "t1", Title: "x", ISRC: "GBUM71029604"}}

	t.Run("enabled uses model TTML", func(t *testing.T) {
		f := &fakeLrcred{ttml: lrcredModelTTML, lrc: "[00:01.00]from lrc"}
		l, err := newTestLrcred(t, f, true).Fetch(context.Background(), track)
		if err != nil || l == nil || !strings.Contains(l.Content, "model") {
			t.Fatalf("lyric=%+v err=%v", l, err)
		}
	})

	t.Run("disabled replaces model TTML with LRC", func(t *testing.T) {
		const lrc = "[ti:x]\n[00:01.00]from lrc"
		f := &fakeLrcred{ttml: lrcredModelTTML, lrc: lrc}
		l, err := newTestLrcred(t, f, false).Fetch(context.Background(), track)
		if err != nil || l == nil || l.Content != lrc {
			t.Fatalf("lyric=%+v err=%v", l, err)
		}
		want := []string{"/s/GBUM71029604.ttml", "/s/GBUM71029604.lrc"}
		if strings.Join(f.paths, ",") != strings.Join(want, ",") {
			t.Fatalf("expected %v, got %v", want, f.paths)
		}
	})

	t.Run("disabled keeps human word-synced TTML over LRC", func(t *testing.T) {
		f := &fakeLrcred{ttml: lrcredHumanTTML, lrc: "[00:01.00]from lrc"}
		l, err := newTestLrcred(t, f, false).Fetch(context.Background(), track)
		if err != nil || l == nil || l.Content != "[00:01.000]human<00:02.000>" {
			t.Fatalf("lyric=%+v err=%v", l, err)
		}
		if len(f.paths) != 1 {
			t.Fatalf("human TTML should need a single request, got %v", f.paths)
		}
	})

	t.Run("disabled without LRC uses non-model TTML", func(t *testing.T) {
		f := &fakeLrcred{ttml: lrcredHumanTTML}
		l, err := newTestLrcred(t, f, false).Fetch(context.Background(), track)
		if err != nil || l == nil || !strings.Contains(l.Content, "human") {
			t.Fatalf("lyric=%+v err=%v", l, err)
		}
	})

	t.Run("disabled without LRC never uses model TTML", func(t *testing.T) {
		f := &fakeLrcred{ttml: lrcredModelTTML}
		l, err := newTestLrcred(t, f, false).Fetch(context.Background(), track)
		if err != nil || l != nil {
			t.Fatalf("lyric=%+v err=%v", l, err)
		}
	})
}

func TestLrcred_FetchSearchAndRank(t *testing.T) {
	f := &fakeLrcred{ttml: lrcredHumanTTML}
	p := newTestLrcred(t, f, true)
	track := &domain.TrackDTO{
		Track:   domain.Track{ID: "t1", Title: "Bohemian Rhapsody", Duration: 356},
		Artists: []*domain.Artist{{Name: "Queen"}},
	}
	l, err := p.Fetch(context.Background(), track)
	if err != nil || l == nil {
		t.Fatalf("lyric=%v err=%v", l, err)
	}
	if got := f.paths[len(f.paths)-1]; got != "/s/GBUM71029604.ttml" {
		t.Fatalf("expected best-ranked ISRC to be fetched, got %v", f.paths)
	}
	if f.searchQs[0] != "Queen Bohemian Rhapsody" {
		t.Fatalf("unexpected search query %q", f.searchQs[0])
	}
}

func TestLrcred_Search(t *testing.T) {
	f := &fakeLrcred{ttml: lrcredHumanTTML}
	res, err := newTestLrcred(t, f, true).Search(context.Background(), "Bohemian Rhapsody", "Queen", 356)
	if err != nil {
		t.Fatal(err)
	}
	if len(res) != 2 {
		t.Fatalf("expected 2 results, got %d", len(res))
	}
	if res[1].Provider != "lrc.red" || res[1].ID != "GBUM71029604" || res[1].Duration != 357 {
		t.Fatalf("unexpected result %+v", res[1])
	}
}

func TestLrcred_EmptyQueryDoesNotRequestFeatured(t *testing.T) {
	f := &fakeLrcred{}
	res, err := newTestLrcred(t, f, true).Search(context.Background(), "", "", 0)
	if err != nil || res != nil || len(f.paths) != 0 {
		t.Fatalf("res=%v err=%v paths=%v", res, err, f.paths)
	}
}
