package wails

import (
	"runtime"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
)

// WindowService manages secondary windows (mini player).
type WindowService struct {
	mainWindow        *application.WebviewWindow
	miniWindow        *application.WebviewWindow
	pendingMiniPlayer bool
}

func NewWindowService() *WindowService {
	return &WindowService{}
}

func (s *WindowService) SetMainWindow(w *application.WebviewWindow) {
	s.mainWindow = w
	// On macOS, fullscreen exit is animated. Wait for the animation to finish
	// before opening the mini player to avoid hiding the window mid-transition.
	w.RegisterHook(events.Common.WindowUnFullscreen, func(_ *application.WindowEvent) {
		if s.pendingMiniPlayer {
			s.pendingMiniPlayer = false
			s.OpenMiniPlayer()
		}
	})
}

func (s *WindowService) SetMiniWindow(w *application.WebviewWindow) {
	s.miniWindow = w
}

func (s *WindowService) OpenMiniPlayer() {
	if s.miniWindow == nil {
		return
	}
	if s.mainWindow != nil {
		s.mainWindow.Hide()
	}
	s.miniWindow.Show()
	s.miniWindow.Focus()
}

func (s *WindowService) CloseMiniPlayer() {
	if s.miniWindow == nil {
		return
	}
	s.miniWindow.Hide()
	if s.mainWindow != nil {
		s.mainWindow.Show()
		s.mainWindow.Focus()
	}
}

func (s *WindowService) ToggleMiniPlayer() {
	if s.miniWindow == nil {
		return
	}
	if s.miniWindow.IsVisible() {
		s.CloseMiniPlayer()
	} else {
		if s.mainWindow.IsFullscreen() {
			if runtime.GOOS == "darwin" {
				s.pendingMiniPlayer = true
				s.mainWindow.UnFullscreen()
				return
			}
			s.mainWindow.UnFullscreen()
		}
		s.OpenMiniPlayer()
	}
}
