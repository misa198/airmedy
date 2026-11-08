//go:build !darwin

package wails

import (
	"airmedy/internal/app/i18n"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"
	"github.com/wailsapp/wails/v3/pkg/application"
)

func SetupTrayPill(_ *application.App, _ *player.PlayerService, _ domain.ArtworkCache, _ *i18n.Service, _ *WindowService, _, _ string, _ []byte) {
}
