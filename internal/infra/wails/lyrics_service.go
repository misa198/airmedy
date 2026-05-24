package wails

import (
	"context"

	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/lyrics"
	"airmedy/internal/domain"
)

type LyricsService struct {
	service         *lyrics.LyricsService
	settingsService *appsettings.SettingsService
}

func NewLyricsService(service *lyrics.LyricsService, settingsService *appsettings.SettingsService) *LyricsService {
	return &LyricsService{service: service, settingsService: settingsService}
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

func (s *LyricsService) SearchLyrics(title, artist string, duration int) ([]*domain.LyricsSearchResult, error) {
	settings, _ := s.settingsService.GetSettings(context.Background())
	enableLrclib, enableKugou := true, true
	if settings != nil {
		enableLrclib = settings.EnableLrclib
		enableKugou = settings.EnableKugou
	}
	return s.service.SearchLyrics(context.Background(), title, artist, duration, enableLrclib, enableKugou)
}

// FetchLyrics fetches lyrics from all enabled providers for the given track.
// Enabled state is read from settings; both providers are queried when both are enabled.
func (s *LyricsService) FetchLyrics(trackID string, track *domain.TrackDTO) (*domain.Lyric, error) {
	settings, _ := s.settingsService.GetSettings(context.Background())
	enableLrclib, enableKugou := true, true
	if settings != nil {
		enableLrclib = settings.EnableLrclib
		enableKugou = settings.EnableKugou
	}
	return s.service.FetchFromProviders(context.Background(), track, enableLrclib, enableKugou)
}
