package wails

import (
	"context"

	"airmedy/internal/app/lyrics"
	"airmedy/internal/domain"
)

type LyricsService struct {
	service *lyrics.LyricsService
}

func NewLyricsService(service *lyrics.LyricsService) *LyricsService {
	return &LyricsService{service: service}
}

func (s *LyricsService) GetLyrics(trackID string) (*domain.Lyric, error) {
	return s.service.GetLyrics(context.Background(), trackID)
}

func (s *LyricsService) SaveLyrics(trackID, content, source string) error {
	return s.service.SaveLyrics(context.Background(), trackID, content, source)
}

func (s *LyricsService) DeleteLyrics(trackID string) error {
	return s.service.DeleteLyrics(context.Background(), trackID)
}

// FetchLyrics fetches lyrics from all enabled providers for the given track.
// Enabled state is read from settings; both providers are queried when both are enabled.
func (s *LyricsService) FetchLyrics(trackID string, track *domain.TrackDTO) (*domain.Lyric, error) {
	return s.service.FetchFromProviders(context.Background(), track, true, true)
}
