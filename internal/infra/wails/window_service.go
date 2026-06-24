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
	miniWindowFactory func() *application.WebviewWindow
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

func (s *WindowService) SetMiniWindowFactory(f func() *application.WebviewWindow) {
	s.miniWindowFactory = f
}

func (s *WindowService) OpenMiniPlayer() {
	if s.miniWindow == nil {
		if s.miniWindowFactory == nil {
			return
		}
		s.miniWindow = s.miniWindowFactory()
	}
	if s.mainWindow != nil {
		s.mainWindow.Hide()
	}
	s.miniWindow.Show()
	s.miniWindow.Focus()
}

// CloseMiniPlayer is called from the frontend or ToggleMiniPlayer.
// It shows the main window and triggers native close so Wails destroys the webview.
func (s *WindowService) CloseMiniPlayer() {
	w := s.miniWindow
	if w == nil {
		return
	}
	s.miniWindow = nil
	if s.mainWindow != nil {
		s.mainWindow.Show()
		s.mainWindow.Focus()
	}
	w.Close()
}

// OnMiniPlayerClosed is called from the WindowClosing hook only.
// The window is already in the process of being destroyed; just clean up references.
func (s *WindowService) OnMiniPlayerClosed() {
	s.miniWindow = nil
	if s.mainWindow != nil {
		s.mainWindow.Show()
		s.mainWindow.Focus()
	}
}

// ShowCurrent brings the currently active window to front. If the mini player
// is open, it is shown; otherwise the main window is shown. Used by the tray
// "Show Airmedy" action to avoid revealing both windows at once.
func (s *WindowService) ShowCurrent() {
	if s.miniWindow != nil {
		s.miniWindow.Show()
		s.miniWindow.Focus()
		return
	}
	if s.mainWindow != nil {
		s.mainWindow.Show()
		s.mainWindow.Focus()
	}
}

// SetTitleBarTheme updates the native title bar colour to match the given app
// theme. Effective on Windows only; no-op on other platforms.
func (s *WindowService) SetTitleBarTheme(theme string) {
	if s.mainWindow == nil {
		return
	}
	setTitleBarThemeImpl(s.mainWindow, theme)
	s.mainWindow.SetBackgroundColour(bgColorForTheme(theme))
}

func bgColorForTheme(theme string) application.RGBA {
	switch theme {
	case "light":
		return application.NewRGB(244, 244, 245)
	case "black":
		return application.NewRGB(10, 10, 10)
	default: // "dark", "system"
		return application.NewRGB(24, 24, 27)
	}
}

func (s *WindowService) ToggleMiniPlayer() {
	if s.miniWindow != nil && s.miniWindow.IsVisible() {
		s.CloseMiniPlayer()
		return
	}
	if s.mainWindow != nil && s.mainWindow.IsFullscreen() {
		if runtime.GOOS == "darwin" {
			s.pendingMiniPlayer = true
			s.mainWindow.UnFullscreen()
			return
		}
		s.mainWindow.UnFullscreen()
	}
	s.OpenMiniPlayer()
}
