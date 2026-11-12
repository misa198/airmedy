package wails

import (
	"context"

	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type SettingsService struct {
	svc           *appsettings.SettingsService
	playerService *player.PlayerService
}

func NewSettingsService(svc *appsettings.SettingsService, playerService *player.PlayerService) *SettingsService {
	svc.AddChangeListener(func(settings *domain.AppSettings) {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("settings:player-indicator", settings.ShowPlayerIndicator)
			app.Event.Emit("settings:player-favorite-button", settings.ShowPlayerFavoriteButton)
			app.Event.Emit("settings:tray-theme", settings.TrayTheme)
			app.Event.Emit("settings:tray-panel-theme", settings.TrayPanelTheme)
			app.Event.Emit("settings:theme", settings.Theme)
			app.Event.Emit("settings:tray-background", settings.TrayBackgroundMode)
			app.Event.Emit("settings:title-bar", map[string]any{"show_navigation": settings.TitleBarShowNavigation, "actions": settings.TitleBarActions})
			app.Event.Emit("settings:sidebar", settings.SidebarItems)
		}
	})
	return &SettingsService{svc: svc, playerService: playerService}
}

func (s *SettingsService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	return s.svc.GetSettings(ctx)
}

func (s *SettingsService) SaveSettings(ctx context.Context, settings *domain.AppSettings) error {
	return s.svc.SaveSettings(ctx, settings)
}

func (s *SettingsService) OpenAppDataFolder(ctx context.Context) error {
	return s.svc.OpenAppDataFolder(ctx)
}

func (s *SettingsService) GetAppInfo(ctx context.Context) *appsettings.AppInfo {
	return s.svc.GetAppInfo(ctx)
}
