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

type job struct {
	ctx     context.Context
	cancel  context.CancelFunc
	key     [32]byte
	lines   []string
	waiters map[uint64]chan response
}
type response struct {
	lines []domain.RomanizedLine
	err   error
}

type Service struct {
	engine       domain.RomanizationEngine
	mu           sync.Mutex
	pending      chan *job
	pendingJob   *job
	active       *job
	nextWaiterID uint64
	stop         chan struct{}
	done         chan struct{}
	closed       bool
	enabled      bool
	idle         time.Duration
}

func (s *Service) Enabled() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.enabled
}

func (s *Service) SetEnabled(enabled bool) {
	s.mu.Lock()
	s.enabled = enabled
	s.mu.Unlock()
}

func New(engine domain.RomanizationEngine) *Service { return newService(engine, 2*time.Minute) }

func newService(engine domain.RomanizationEngine, idle time.Duration) *Service {
	s := &Service{engine: engine, pending: make(chan *job, 1), stop: make(chan struct{}), done: make(chan struct{}), idle: idle}
	go s.run()
	return s
}

func (s *Service) Romanize(ctx context.Context, lines []string) ([]domain.RomanizedLine, error) {
	if err := validate(lines); err != nil {
		return nil, err
	}
	key := s.key(lines)
	result := make(chan response, 1)
	s.mu.Lock()
	if s.closed {
		s.mu.Unlock()
		return nil, context.Canceled
	}
	s.nextWaiterID++
	waiterID := s.nextWaiterID
	if s.active != nil && s.active.key == key && s.active.ctx.Err() == nil {
		active := s.active
		active.waiters[waiterID] = result
		s.mu.Unlock()
		return s.wait(ctx, active, waiterID, result)
	}
	if s.pendingJob != nil && s.pendingJob.key == key && s.pendingJob.ctx.Err() == nil {
		pending := s.pendingJob
		pending.waiters[waiterID] = result
		s.mu.Unlock()
		return s.wait(ctx, pending, waiterID, result)
	}
	if s.active != nil {
		s.active.cancel()
		s.respondLocked(s.active, response{err: context.Canceled})
	}
	if s.pendingJob != nil {
		s.pendingJob.cancel()
		select {
		case old := <-s.pending:
			s.respondLocked(old, response{err: context.Canceled})
		default:
		}
	}
	jobCtx, cancel := context.WithCancel(context.Background())
	r := &job{ctx: jobCtx, cancel: cancel, key: key, lines: append([]string(nil), lines...), waiters: map[uint64]chan response{waiterID: result}}
	s.pendingJob = r
	s.pending <- r
	s.mu.Unlock()
	return s.wait(ctx, r, waiterID, result)
}

func (s *Service) Close(ctx context.Context) error {
	s.mu.Lock()
	if !s.closed {
		s.closed = true
		close(s.stop)
		if s.active != nil {
			s.active.cancel()
			s.respondLocked(s.active, response{err: context.Canceled})
		}
		if s.pendingJob != nil {
			s.pendingJob.cancel()
			select {
			case old := <-s.pending:
				s.respondLocked(old, response{err: context.Canceled})
			default:
			}
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
			if s.pendingJob == r {
				s.pendingJob = nil
			}
			if s.closed || r.ctx.Err() != nil {
				s.respondLocked(r, response{err: context.Canceled})
				s.mu.Unlock()
				continue
			}
			s.active = r
			s.mu.Unlock()
			var result []domain.RomanizedLine
			var err error
			if cached != nil && r.key == cachedKey {
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
						cachedKey = r.key
						cached = append([]domain.RomanizedLine(nil), result...)
					}
				}
			}
			if r.ctx.Err() != nil {
				err = r.ctx.Err()
			}
			s.mu.Lock()
			if s.active == r {
				s.active = nil
			}
			s.respondLocked(r, response{lines: result, err: err})
			s.mu.Unlock()
			timer.Reset(s.idle)
		}
	}
}

func (s *Service) key(lines []string) [32]byte {
	payload, _ := json.Marshal(struct {
		Version string
		Lines   []string
	}{s.engine.Version(), lines})
	return sha256.Sum256(payload)
}

func (s *Service) wait(ctx context.Context, job *job, waiterID uint64, result <-chan response) ([]domain.RomanizedLine, error) {
	stop := context.AfterFunc(ctx, func() { s.removeWaiter(job, waiterID) })
	defer stop()
	select {
	case <-ctx.Done():
		s.removeWaiter(job, waiterID)
		return nil, ctx.Err()
	case response := <-result:
		return append([]domain.RomanizedLine(nil), response.lines...), response.err
	}
}

func (s *Service) removeWaiter(job *job, waiterID uint64) {
	s.mu.Lock()
	defer s.mu.Unlock()
	delete(job.waiters, waiterID)
	if len(job.waiters) == 0 {
		job.cancel()
	}
}

func (s *Service) respondLocked(job *job, result response) {
	for _, waiter := range job.waiters {
		waiter <- response{lines: append([]domain.RomanizedLine(nil), result.lines...), err: result.err}
	}
	job.waiters = nil
}

func (s *Service) convert(ctx context.Context, lines []string) ([]domain.RomanizedLine, error) {
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
		text, err := s.engine.Romanize(ctx, line)
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
