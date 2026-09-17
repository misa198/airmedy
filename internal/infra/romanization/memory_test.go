//go:build darwin || linux

package romanization

import (
	"airmedy/internal/app/romanization"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"runtime/pprof"
	"syscall"
	"testing"
	"time"
)

func memorySample() (uint64, int64) {
	runtime.GC() // Measurement only; never used by production code.
	var m runtime.MemStats
	runtime.ReadMemStats(&m)
	var usage syscall.Rusage
	_ = syscall.Getrusage(syscall.RUSAGE_SELF, &usage)
	rss := usage.Maxrss
	if runtime.GOOS == "linux" {
		rss *= 1024
	}
	return m.HeapAlloc, rss
}

// Run in a fresh non-race process; the race runtime changes memory accounting.
func TestMemoryBudget(t *testing.T) {
	if os.Getenv("AIRMEDY_ROMANIZATION_MEMORY") != "1" {
		t.Skip("set AIRMEDY_ROMANIZATION_MEMORY=1 for memory acceptance workload")
	}
	baseline, baseRSS := memorySample()
	goroutines := runtime.NumGoroutine()
	e := New()
	ctx := context.Background()
	var loaded, peak uint64
	for cycle := 0; cycle < 10; cycle++ {
		start := time.Now()
		for song := 0; song < 10; song++ {
			for language, text := range []string{"東京へ行く、私は学生です Hello!", "重庆银行你好世界", "같이 꽃잎 한글"} {
				if _, err := e.Romanize(ctx, fmt.Sprintf("%d %s", song, text), language == 0); err != nil {
					t.Fatal(err)
				}
			}
		}
		heap, rss := memorySample()
		loaded = max(loaded, heap-baseline)
		if rss > baseRSS {
			peak = max(peak, uint64(rss-baseRSS))
		}
		t.Logf("cycle %d: cold + 10 songs=%s, live delta=%.1f MiB, peak RSS delta=%.1f MiB", cycle, time.Since(start), float64(heap-baseline)/(1<<20), float64(rss-baseRSS)/(1<<20))
		e.Release()
		released, _ := memorySample()
		if released > baseline+8<<20 {
			t.Fatalf("retained heap after release: %.1f MiB", float64(released-baseline)/(1<<20))
		}
	}
	if loaded > 128<<20 || peak > 256<<20 {
		t.Fatalf("budget exceeded: heap %.1f MiB; peak RSS %.1f MiB", float64(loaded)/(1<<20), float64(peak)/(1<<20))
	}
	for i := 0; i < 100; i++ {
		s := romanization.New(e)
		_, _ = s.Romanize(ctx, []string{"한글"})
		if err := s.Close(ctx); err != nil {
			t.Fatal(err)
		}
	}
	runtime.GC()
	if runtime.NumGoroutine() > goroutines+1 {
		t.Fatal("worker goroutine leak")
	}
	path := filepath.Join(t.TempDir(), "released-heap.pprof")
	if requested := os.Getenv("AIRMEDY_ROMANIZATION_HEAP_PROFILE"); requested != "" {
		path = requested
	}
	f, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	if err := pprof.WriteHeapProfile(f); err != nil {
		t.Fatal(err)
	}
	if err := f.Close(); err != nil {
		t.Fatal(err)
	}
	t.Logf("released heap profile: %s", path)
}
