package lyrics

import (
	"context"
	"errors"
	"log/slog"

	"airmedy/internal/domain"
)

type LyricsService struct {
	repo      domain.LyricRepository
	logger    *slog.Logger
	providers []domain.LyricsProvider
}

func NewLyricsService(repo domain.LyricRepository, logger *slog.Logger, providers []domain.LyricsProvider) *LyricsService {
	return &LyricsService{repo: repo, logger: logger, providers: providers}
}

func (s *LyricsService) GetLyrics(ctx context.Context, trackID string) (*domain.Lyric, error) {
	return s.repo.GetByTrackID(ctx, trackID)
}

func (s *LyricsService) SaveLyrics(ctx context.Context, trackID, content, source string) error {
	existing, _ := s.repo.GetByTrackID(ctx, trackID)
	if existing != nil {
		existing.Content = content
		existing.Source = source
		return s.repo.Upsert(ctx, existing)
	}
	return s.repo.Upsert(ctx, &domain.Lyric{
		TrackID: trackID,
		Content: content,
		Source:  source,
	})
}

func (s *LyricsService) DeleteLyrics(ctx context.Context, trackID string) error {
	return s.repo.Delete(ctx, trackID)
}

func (s *LyricsService) SearchLyrics(ctx context.Context, title, artist string, duration int, enableLrclib, enableKugou bool) ([]*domain.LyricsSearchResult, error) {
	var active []domain.LyricsProvider
	for _, p := range s.providers {
		if (p.Name() == "lrclib" && enableLrclib) || (p.Name() == "kugou" && enableKugou) {
			active = append(active, p)
		}
	}

	if len(active) == 0 {
		return nil, nil
	}

	type providerRes struct {
		results []*domain.LyricsSearchResult
		err     error
	}
	ch := make(chan providerRes, len(active))

	for _, p := range active {
		go func(p domain.LyricsProvider) {
			res, err := p.Search(ctx, title, artist, duration)
			ch <- providerRes{res, err}
		}(p)
	}

	var allResults []*domain.LyricsSearchResult
	for range len(active) {
		res := <-ch
		if res.err != nil {
			s.logger.Warn("lyrics provider search failed", "error", res.err)
			continue
		}
		allResults = append(allResults, res.results...)
	}

	return allResults, nil
}

// SaveMetaLyrics saves lyrics extracted from file metadata.
// Preserves any existing provider content/source fields.
func (s *LyricsService) SaveMetaLyrics(ctx context.Context, trackID, content, source string) error {
	existing, _ := s.repo.GetByTrackID(ctx, trackID)
	if existing != nil {
		existing.MetaContent = content
		existing.MetaSource = source
		return s.repo.Upsert(ctx, existing)
	}
	return s.repo.Upsert(ctx, &domain.Lyric{
		TrackID:     trackID,
		MetaContent: content,
		MetaSource:  source,
	})
}

// ResolveLyrics returns the best available cached lyric based on the preferMetadata flag.
// Returns nil if no cached lyric is available (caller should fetch from providers).
func (s *LyricsService) ResolveLyrics(ctx context.Context, trackID string, preferMetadata bool) *domain.Lyric {
	lyric, _ := s.repo.GetByTrackID(ctx, trackID)
	if lyric == nil {
		return nil
	}

	if preferMetadata && lyric.MetaContent != "" {
		return &domain.Lyric{
			TrackID: trackID,
			Content: lyric.MetaContent,
			Source:  lyric.MetaSource,
		}
	}
	if lyric.Content != "" {
		return lyric
	}
	return nil
}

// FetchFromProviders fetches lyrics from enabled providers concurrently.
// When both are enabled, the first non-nil result wins and the other request is cancelled.
func (s *LyricsService) FetchFromProviders(ctx context.Context, track *domain.TrackDTO, enableLrclib, enableKugou bool) (*domain.Lyric, error) {
	var active []domain.LyricsProvider
	for _, p := range s.providers {
		if (p.Name() == "lrclib" && enableLrclib) || (p.Name() == "kugou" && enableKugou) {
			active = append(active, p)
		}
	}

	switch len(active) {
	case 0:
		return nil, nil
	case 1:
		l, err := active[0].Fetch(ctx, track)
		if err != nil || l == nil {
			return nil, err
		}
		return s.saveLyric(ctx, l.TrackID, l.Content, l.Source)
	default:
		return s.fetchRace(ctx, track, active)
	}
}

func (s *LyricsService) fetchRace(ctx context.Context, track *domain.TrackDTO, providers []domain.LyricsProvider) (*domain.Lyric, error) {
	raceCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	type result struct {
		lyric *domain.Lyric
		err   error
	}
	ch := make(chan result, len(providers))

	for _, p := range providers {
		go func(p domain.LyricsProvider) {
			l, e := p.Fetch(raceCtx, track)
			ch <- result{l, e}
		}(p)
	}

	var lastErr error
	for range len(providers) {
		r := <-ch
		if r.lyric != nil {
			cancel()
			return s.saveLyric(ctx, r.lyric.TrackID, r.lyric.Content, r.lyric.Source)
		}
		if r.err != nil && !errors.Is(r.err, context.Canceled) {
			lastErr = r.err
		}
	}
	return nil, lastErr
}

func (s *LyricsService) saveLyric(ctx context.Context, trackID, content, source string) (*domain.Lyric, error) {
	existing, _ := s.repo.GetByTrackID(ctx, trackID)
	var lyric *domain.Lyric
	if existing != nil {
		existing.Content = content
		existing.Source = source
		lyric = existing
	} else {
		lyric = &domain.Lyric{TrackID: trackID, Content: content, Source: source}
	}

	if err := s.repo.Upsert(ctx, lyric); err != nil {
		s.logger.Warn("failed to save fetched lyrics", "track_id", trackID, "error", err)
	}
	return lyric, nil
}
