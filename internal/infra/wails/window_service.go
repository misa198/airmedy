package wails

import "github.com/wailsapp/wails/v3/pkg/application"

// WindowService manages secondary windows (mini player).
type WindowService struct {
	mainWindow *application.WebviewWindow
	miniWindow *application.WebviewWindow
}

func NewWindowService() *WindowService {
	return &WindowService{}
}

func (s *WindowService) SetMainWindow(w *application.WebviewWindow) {
	s.mainWindow = w
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
			s.mainWindow.UnFullscreen()
		}
		s.OpenMiniPlayer()
	}
}
