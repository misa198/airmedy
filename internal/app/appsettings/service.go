package appsettings

import (
	"context"
	"fmt"
	"log/slog"
	"os"
	"runtime"
	"sync"

	"airmedy/internal/app/config"
	"airmedy/internal/domain"

	"github.com/emersion/go-autostart"
	"github.com/pkg/browser"
)

type SettingsService struct {
	repo   domain.SettingsRepository
	cfg    *config.Config
	logger *slog.Logger

	cache *domain.AppSettings
	mu    sync.RWMutex

	changeListeners []func(*domain.AppSettings)
	listenersMu     sync.RWMutex
}

// AddChangeListener registers a callback invoked (synchronously) after settings
// are successfully saved, with the newly-persisted settings. Used to react to
// changes without the settings package importing the reacting one.
func (s *SettingsService) AddChangeListener(l func(*domain.AppSettings)) {
	s.listenersMu.Lock()
	defer s.listenersMu.Unlock()
	s.changeListeners = append(s.changeListeners, l)
}

func (s *SettingsService) notifyChange(settings *domain.AppSettings) {
	s.listenersMu.RLock()
	listeners := make([]func(*domain.AppSettings), len(s.changeListeners))
	copy(listeners, s.changeListeners)
	s.listenersMu.RUnlock()
	for _, l := range listeners {
		l(settings)
	}
}

func NewSettingsService(repo domain.SettingsRepository, cfg *config.Config, logger *slog.Logger) *SettingsService {
	return &SettingsService{
		repo:   repo,
		cfg:    cfg,
		logger: logger,
	}
}

func (s *SettingsService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	s.mu.RLock()
	if s.cache != nil {
		defer s.mu.RUnlock()
		return s.cache, nil
	}
	s.mu.RUnlock()

	s.mu.Lock()
	defer s.mu.Unlock()

	// Double check after acquiring lock
	if s.cache != nil {
		return s.cache, nil
	}

	settings, err := s.repo.Load(ctx)
	if err != nil {
		s.logger.Error("failed to load app settings, using defaults", "error", err)
		s.cache = &domain.AppSettings{
			Language:                        "en",
			Theme:                           "system",
			PrimaryColor:                    domain.DefaultPrimaryColor,
			StartAtLogin:                    false,
			ShowTrayIcon:                    true,
			AutoCheckUpdate:                 true,
			EQEnabled:                       true,
			BlendArtworkDuringCrossfade:     true,
			HighContrastLyrics:              true,
			LivingArtworkBackground:         true,
			AutoAdvanceNotificationsEnabled: true,
			ArtistDelimiters:                domain.DefaultDelimiters(),
			AlbumArtistDelimiters:           domain.DefaultDelimiters(),
			GenreDelimiters:                 domain.DefaultDelimiters(),
			ComposerDelimiters:              domain.DefaultDelimiters(),
		}
		return s.cache, nil
	}
	if runtime.GOOS == "windows" || runtime.GOOS == "linux" {
		settings.ShowTrayIcon = true
	}
	s.cache = settings
	return settings, nil
}

func (s *SettingsService) SaveSettings(ctx context.Context, settings *domain.AppSettings) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if runtime.GOOS == "windows" || runtime.GOOS == "linux" {
		settings.ShowTrayIcon = true
	}

	primaryColor, err := domain.NormalizePrimaryColor(settings.PrimaryColor)
	if err != nil {
		return fmt.Errorf("invalid primary color: %w", err)
	}
	settings.PrimaryColor = primaryColor

	// Validate user-configured delimiters before persisting.
	for label, list := range map[string][]string{
		"artist":       settings.ArtistDelimiters,
		"album artist": settings.AlbumArtistDelimiters,
		"genre":        settings.GenreDelimiters,
		"composer":     settings.ComposerDelimiters,
	} {
		if err := domain.ValidateDelimiters(list); err != nil {
			return fmt.Errorf("invalid %s delimiters: %w", label, err)
		}
	}

	err = s.repo.Save(ctx, settings)
	if err != nil {
		s.logger.Error("failed to save app settings", "error", err)
		return fmt.Errorf("failed to save app settings: %w", err)
	}

	s.cache = settings

	// Update autostart
	if err := s.updateAutostart(settings.StartAtLogin); err != nil {
		s.logger.Warn("failed to update autostart setting", "error", err)
	}

	s.notifyChange(settings)

	return nil
}

func (s *SettingsService) OpenAppDataFolder(ctx context.Context) error {
	s.logger.Info("opening app data folder", "path", s.cfg.DataDir)
	return browser.OpenFile(s.cfg.DataDir)
}

type AppInfo struct {
	Name        string `json:"name"`
	Version     string `json:"version"`
	Description string `json:"description"`
	GitHubURL   string `json:"github_url"`
	LicenseURL  string `json:"license_url"`
}

func (s *SettingsService) GetAppInfo(ctx context.Context) *AppInfo {
	return &AppInfo{
		Name:        config.AppName,
		Version:     config.Version,
		Description: config.AppDesc,
		GitHubURL:   config.GitHubURL,
		LicenseURL:  config.LicenseURL,
	}
}

func (s *SettingsService) updateAutostart(enabled bool) error {
	execPath, err := os.Executable()
	if err != nil {
		return fmt.Errorf("failed to get executable path: %w", err)
	}

	app := &autostart.App{
		Name:        "airmedy",
		DisplayName: "Airmedy",
		Exec:        []string{execPath},
	}

	if enabled {
		if !app.IsEnabled() {
			if err := app.Enable(); err != nil {
				return fmt.Errorf("failed to enable autostart: %w", err)
			}
		}
	} else {
		if app.IsEnabled() {
			if err := app.Disable(); err != nil {
				return fmt.Errorf("failed to disable autostart: %w", err)
			}
		}
	}

	return nil
}
