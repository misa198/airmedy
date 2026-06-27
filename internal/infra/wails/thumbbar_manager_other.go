//go:build !windows

package wails

import (
	"airmedy/internal/app/player"

	"github.com/wailsapp/wails/v3/pkg/application"
)

// ThumbBarManager is a no-op stub on non-Windows platforms.
type ThumbBarManager struct{}

func NewThumbBarManager(_ *player.PlayerService) *ThumbBarManager {
	return &ThumbBarManager{}
}

func (m *ThumbBarManager) Setup(_ *application.WebviewWindow) {}
func (m *ThumbBarManager) Stop()                              {}
