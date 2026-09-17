package romanization

import (
	"airmedy/internal/domain"
	"context"
	"crypto/sha256"
	"encoding/json"
	"strings"
	"sync"
	"time"
)

type request struct {
	ctx    context.Context
	cancel context.CancelFunc
	lines  []string
	result chan response
}
type response struct {
	lines []domain.RomanizedLine
	err   error
}

type Service struct {
	engine  domain.RomanizationEngine
	mu      sync.Mutex
	pending chan request
	active  context.CancelFunc
	stop    chan struct{}
	done    chan struct{}
	closed  bool
	idle    time.Duration
}

func New(engine domain.RomanizationEngine) *Service { return newService(engine, 2*time.Minute) }

func newService(engine domain.RomanizationEngine, idle time.Duration) *Service {
	s := &Service{engine: engine, pending: make(chan request, 1), stop: make(chan struct{}), done: make(chan struct{}), idle: idle}
	go s.run()
	return s
}

func (s *Service) Romanize(ctx context.Context, lines []string) ([]domain.RomanizedLine, error) {
	if err := validate(lines); err != nil {
		return nil, err
	}
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	r := request{ctx: ctx, cancel: cancel, lines: append([]string(nil), lines...), result: make(chan response, 1)}
	s.mu.Lock()
	if s.closed {
		s.mu.Unlock()
		return nil, context.Canceled
	}
	if s.active != nil {
		s.active()
	}
	select {
	case old := <-s.pending:
		old.cancel()
	default:
	}
	s.pending <- r
	s.mu.Unlock()
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	case result := <-r.result:
		return result.lines, result.err
	}
}

func (s *Service) Close(ctx context.Context) error {
	s.mu.Lock()
	if !s.closed {
		s.closed = true
		close(s.stop)
		if s.active != nil {
			s.active()
		}
		select {
		case old := <-s.pending:
			old.cancel()
		default:
		}
	}
	s.mu.Unlock()
	select {
	case <-s.done:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (s *Service) run() {
	defer close(s.done)
	defer s.engine.Release()
	timer := time.NewTimer(s.idle)
	defer timer.Stop()
	var cachedKey [32]byte
	var cached []domain.RomanizedLine
	for {
		select {
		case <-s.stop:
			return
		case <-timer.C:
			s.engine.Release()
		case r := <-s.pending:
			s.mu.Lock()
			if s.closed {
				r.cancel()
			}
			s.active = r.cancel
			s.mu.Unlock()
			payload, _ := json.Marshal(struct {
				Version string
				Lines   []string
			}{s.engine.Version(), r.lines})
			key := sha256.Sum256(payload)
			var result []domain.RomanizedLine
			var err error
			if cached != nil && key == cachedKey {
				result = append([]domain.RomanizedLine(nil), cached...)
			} else {
				result, err = s.convert(r.ctx, r.lines)
				cached = nil // Keep only the latest request, including failures.
				if err == nil {
					encoded, _ := json.Marshal(result)
					cacheable := len(encoded) <= 1<<20
					for _, line := range result {
						cacheable = cacheable && line.Status != "failed"
					}
					if cacheable {
						cachedKey = key
						cached = append([]domain.RomanizedLine(nil), result...)
					}
				}
			}
			if r.ctx.Err() != nil {
				err = r.ctx.Err()
			}
			r.result <- response{result, err}
			s.mu.Lock()
			s.active = nil
			s.mu.Unlock()
			timer.Reset(s.idle)
		}
	}
}

func (s *Service) convert(ctx context.Context, lines []string) ([]domain.RomanizedLine, error) {
	inspection, _ := Inspect(lines)
	japanese := false
	for _, lang := range inspection.Languages {
		japanese = japanese || lang == "ja"
	}
	result := make([]domain.RomanizedLine, len(lines))
	for i, line := range lines {
		if err := ctx.Err(); err != nil {
			return nil, err
		}
		result[i].Status = "unsupported"
		info, _ := Inspect([]string{line})
		if !info.Supported {
			continue
		}
		text, err := s.engine.Romanize(ctx, line, japanese)
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		if err != nil || text == "" || text == line {
			result[i].Status = "failed"
			continue
		}
		result[i] = domain.RomanizedLine{Text: strings.Clone(text), Status: "converted"}
	}
	return result, nil
}
