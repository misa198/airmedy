package wails

import (
	"context"

	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/lyrics"
	"airmedy/internal/app/romanization"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type LyricsService struct {
	service         *lyrics.LyricsService
	settingsService *appsettings.SettingsService
	romanization    *romanization.Service
}

func NewLyricsService(service *lyrics.LyricsService, settingsService *appsettings.SettingsService, romanizer *romanization.Service) *LyricsService {
	return &LyricsService{service: service, settingsService: settingsService, romanization: romanizer}
}

func (s *LyricsService) InspectRomanization(lines []string) (domain.RomanizationInspection, error) {
	return romanization.Inspect(lines)
}

func (s *LyricsService) RomanizeLyrics(ctx context.Context, lines []string) ([]domain.RomanizedLine, error) {
	return s.romanization.Romanize(ctx, lines)
}

func (s *LyricsService) GetRomanizationEnabled() bool {
	return s.romanization.Enabled()
}

func (s *LyricsService) SetRomanizationEnabled(enabled bool) {
	s.romanization.SetEnabled(enabled)
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("lyrics:romanization-enabled", enabled)
	}
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

func (s *LyricsService) SaveLyricsFile(audioPath, content string) error {
	return s.service.SaveLyricsFile(context.Background(), audioPath, content)
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
