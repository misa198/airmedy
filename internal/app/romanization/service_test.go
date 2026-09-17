package romanization

import (
	"airmedy/internal/domain"
	"context"
	"errors"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

type fakeEngine struct {
	calls    atomic.Int32
	releases atomic.Int32
	version  string
	convert  func(context.Context, string, bool) (string, error)
}

func (f *fakeEngine) Version() string { return f.version }
func (f *fakeEngine) Release()        { f.releases.Add(1) }
func (f *fakeEngine) Romanize(ctx context.Context, s string, ja bool) (string, error) {
	f.calls.Add(1)
	if f.convert != nil {
		return f.convert(ctx, s, ja)
	}
	return "reading " + s, nil
}

func TestInspectAndLimits(t *testing.T) {
	for _, tc := range []struct {
		lines     []string
		languages string
		mandarin  bool
	}{
		{[]string{"Hello world", "123"}, "", false},
		{[]string{"你好", "世界"}, "zh", true},
		{[]string{"世界", "こんにちは", "한글"}, "ja,ko", false},
		{[]string{"ﾊﾛｰ", "한"}, "ja,ko", false},
	} {
		got, err := Inspect(tc.lines)
		if err != nil || strings.Join(got.Languages, ",") != tc.languages || got.MandarinDefault != tc.mandarin {
			t.Fatalf("%+v %v", got, err)
		}
	}
	for _, lines := range [][]string{make([]string, 2001), {strings.Repeat("a", 2049)}, {"\xff"}, strings.Split(strings.Repeat(strings.Repeat("a", 1024)+"\n", 129), "\n")} {
		if _, err := Inspect(lines); err == nil {
			t.Fatal("accepted oversized/invalid request")
		}
	}
}

func TestEnabled(t *testing.T) {
	s := New(&fakeEngine{})
	t.Cleanup(func() { _ = s.Close(context.Background()) })
	if s.Enabled() {
		t.Fatal("enabled by default")
	}
	s.SetEnabled(true)
	if !s.Enabled() {
		t.Fatal("enabled state was not retained")
	}
}

func TestRomanizeSharesMatchingRequests(t *testing.T) {
	entered := make(chan struct{})
	release := make(chan struct{})
	f := &fakeEngine{convert: func(context.Context, string, bool) (string, error) {
		close(entered)
		<-release
		return "reading", nil
	}}
	s := New(f)
	t.Cleanup(func() { _ = s.Close(context.Background()) })
	first := make(chan []domain.RomanizedLine, 1)
	second := make(chan []domain.RomanizedLine, 1)
	go func() { result, _ := s.Romanize(context.Background(), []string{"世界"}); first <- result }()
	<-entered
	go func() { result, _ := s.Romanize(context.Background(), []string{"世界"}); second <- result }()
	close(release)
	left, right := <-first, <-second
	if f.calls.Load() != 1 || left[0].Text != "reading" || right[0].Text != "reading" {
		t.Fatalf("calls=%d left=%+v right=%+v", f.calls.Load(), left, right)
	}
	left[0].Text = "mutated"
	if right[0].Text != "reading" {
		t.Fatal("shared callers received aliased results")
	}
}

func TestCacheContextAndFallback(t *testing.T) {
	f := &fakeEngine{version: "v1"}
	s := New(f)
	t.Cleanup(func() {
		if err := s.Close(context.Background()); err != nil {
			t.Error(err)
		}
	})
	f.convert = func(_ context.Context, text string, ja bool) (string, error) {
		if !ja {
			t.Error("Han-only line lost song Japanese context")
		}
		return "romaji", nil
	}
	lines := []string{"世界", "かな", "Latin"}
	first, err := s.Romanize(context.Background(), lines)
	if err != nil || first[2].Status != "unsupported" {
		t.Fatalf("%+v %v", first, err)
	}
	first[0].Text = "caller mutation"
	second, _ := s.Romanize(context.Background(), lines)
	if f.calls.Load() != 2 || second[0].Text != "romaji" {
		t.Fatal("cache miss or aliased cache")
	}
	_, _ = s.Romanize(context.Background(), []string{"かな"})
	if f.calls.Load() != 3 {
		t.Fatal("content did not invalidate cache")
	}
	// Requests are complete before changing the fake's version or behavior.
	f.version = "v2"
	_, _ = s.Romanize(context.Background(), []string{"かな"})
	if f.calls.Load() != 4 {
		t.Fatal("version did not invalidate cache")
	}
	f.convert = func(context.Context, string, bool) (string, error) { return "", errors.New("no reading") }
	failed, _ := s.Romanize(context.Background(), []string{"違う"})
	if failed[0].Status != "failed" {
		t.Fatal(failed)
	}
	_, _ = s.Romanize(context.Background(), []string{"違う"})
	if f.calls.Load() != 6 {
		t.Fatal("failure was cached; retry cannot succeed")
	}
}

func TestCancellationLatestPendingAndShutdown(t *testing.T) {
	entered := make(chan string, 3)
	release := make(chan struct{})
	f := &fakeEngine{}
	f.convert = func(ctx context.Context, text string, _ bool) (string, error) {
		entered <- text
		if text == "一" {
			<-release
		} // Model a non-cancellable dictionary loader.
		return "reading", ctx.Err()
	}
	s := New(f)
	first := make(chan error, 1)
	go func() { _, err := s.Romanize(context.Background(), []string{"一"}); first <- err }()
	<-entered
	secondCtx, cancel := context.WithCancel(context.Background())
	second := make(chan error, 1)
	go func() { _, err := s.Romanize(secondCtx, []string{"二"}); second <- err }()
	if err := <-first; !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
	third := make(chan []domain.RomanizedLine, 1)
	go func() { r, _ := s.Romanize(context.Background(), []string{"三"}); third <- r }()
	if err := <-second; !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
	cancel()
	close(release)
	if text := <-entered; text != "三" {
		t.Fatalf("ran stale pending %s", text)
	}
	if r := <-third; len(r) != 1 || r[0].Text != "reading" {
		t.Fatal(r)
	}
	if err := s.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
	if _, err := s.Romanize(context.Background(), []string{"一"}); !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
	if f.releases.Load() != 1 {
		t.Fatal("shutdown did not release engine")
	}
}

func TestIdleRelease(t *testing.T) {
	f := &fakeEngine{}
	s := newService(f, 10*time.Millisecond)
	t.Cleanup(func() {
		if err := s.Close(context.Background()); err != nil {
			t.Error(err)
		}
	})
	_, _ = s.Romanize(context.Background(), []string{"한글"})
	deadline := time.After(time.Second)
	for f.releases.Load() == 0 {
		select {
		case <-deadline:
			t.Fatal("idle dictionary retained")
		case <-time.After(time.Millisecond):
		}
	}
}

func TestCachePayloadLimitAndCancellationBetweenLines(t *testing.T) {
	f := &fakeEngine{convert: func(context.Context, string, bool) (string, error) {
		return strings.Repeat("a", 1<<20), nil
	}}
	s := New(f)
	t.Cleanup(func() {
		if err := s.Close(context.Background()); err != nil {
			t.Error(err)
		}
	})
	for range 2 {
		if _, err := s.Romanize(context.Background(), []string{"中"}); err != nil {
			t.Fatal(err)
		}
	}
	if f.calls.Load() != 2 {
		t.Fatal("oversized payload cached")
	}
	ctx, cancel := context.WithCancel(context.Background())
	f.convert = func(context.Context, string, bool) (string, error) { cancel(); return "reading", nil }
	if _, err := s.Romanize(ctx, []string{"一", "二"}); !errors.Is(err, context.Canceled) {
		t.Fatal(err)
	}
	// Close joins the worker before reading the count.
	if err := s.Close(context.Background()); err != nil {
		t.Fatal(err)
	}
	if f.calls.Load() < 3 || f.calls.Load() > 4 {
		t.Fatalf("unexpected conversion count after cancellation: %d", f.calls.Load())
	}
}
