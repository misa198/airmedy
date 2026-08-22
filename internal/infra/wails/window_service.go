package wails

import (
	"context"
	"runtime"
	"sync"
	"time"

	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
)

// Mini player size constraints (kept in sync with the window factory options).
const (
	miniMinWidth = 280
	miniMaxSize  = 500
)

// miniSaveDebounce coalesces the stream of move/resize events into one DB write.
const miniSaveDebounce = 400 * time.Millisecond

// WindowService manages secondary windows (mini player).
type WindowService struct {
	mainWindow        *application.WebviewWindow
	miniWindow        *application.WebviewWindow
	miniWindowFactory func() *application.WebviewWindow
	pendingMiniPlayer bool

	miniRepo    domain.MiniPlayerStateRepository
	miniState   *domain.MiniPlayerState
	miniMu      sync.Mutex
	miniSaveTmr *time.Timer
}

func NewWindowService(miniRepo domain.MiniPlayerStateRepository) *WindowService {
	return &WindowService{miniRepo: miniRepo}
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

// ShowMain returns to the primary app window. If the mini player is open, it
// stays alive but is hidden so it cannot obscure the main window that was
// explicitly requested by a notification click.
func (s *WindowService) ShowMain() {
	if s.miniWindow != nil {
		s.miniWindow.Hide()
	}
	if s.mainWindow != nil {
		// Do not restore here: notification activation must preserve the user's
		// maximized window state. Show brings a hidden/minimised window forward
		// without changing its frame.
		showAndFocus(s.mainWindow)
	}
}

// showAndFocus reveals an existing window without altering its size or state.
// In particular, Restore must not be called here because it exits maximized mode.
func showAndFocus(window interface {
	Show() application.Window
	Focus()
}) {
	window.Show()
	window.Focus()
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

// loadMiniState lazily loads and caches the persisted mini player state.
func (s *WindowService) loadMiniState() *domain.MiniPlayerState {
	if s.miniState != nil {
		return s.miniState
	}
	if s.miniRepo == nil {
		s.miniState = &domain.MiniPlayerState{}
		return s.miniState
	}
	state, err := s.miniRepo.Load(context.Background())
	if err != nil || state == nil {
		state = &domain.MiniPlayerState{}
	}
	s.miniState = state
	return s.miniState
}

// ApplyMiniState restores the saved geometry and pin mode onto a freshly created
// mini window. Called from the window factory before the window is shown.
func (s *WindowService) ApplyMiniState(w *application.WebviewWindow) {
	state := s.loadMiniState()
	if state.HasPosition {
		rect := s.clampToScreen(w, application.Rect{
			X:      state.X,
			Y:      state.Y,
			Width:  state.Width,
			Height: state.Height,
		})
		w.SetBounds(rect)
	}
	w.SetAlwaysOnTop(state.AlwaysOnTop)
}

// clampToScreen ensures the rectangle has a valid size and lies fully within the
// work area of the screen it lands on. This keeps the window reachable after a
// screen layout change (lower resolution, disconnected monitor, etc.).
func (s *WindowService) clampToScreen(w *application.WebviewWindow, rect application.Rect) application.Rect {
	rect = squareMiniRect(rect)

	// Position the window first so GetScreen resolves the nearest screen.
	w.SetBounds(rect)
	screen, err := w.GetScreen()
	if err != nil || screen == nil {
		return rect
	}
	return clampSquareRectToWorkArea(rect, screen.WorkArea)
}

// squareMiniRect restores legacy rectangular geometry as a square without
// shrinking the player artwork.
func squareMiniRect(rect application.Rect) application.Rect {
	side := clampInt(max(rect.Width, rect.Height), miniMinWidth, miniMaxSize)
	rect.Width = side
	rect.Height = side
	return rect
}

// clampSquareRectToWorkArea keeps a square rectangle reachable. A work area
// smaller than the configured minimum is an unavoidable OS-level constraint.
func clampSquareRectToWorkArea(rect, wa application.Rect) application.Rect {
	side := min(rect.Width, wa.Width, wa.Height)
	rect.Width = side
	rect.Height = side
	rect.X = clampInt(rect.X, wa.X, wa.X+wa.Width-side)
	rect.Y = clampInt(rect.Y, wa.Y, wa.Y+wa.Height-side)
	return rect
}

// clampRectToWorkArea shrinks rect to fit within the work area if needed, then
// moves it so it lies fully inside the work area. Pure function for testability.
func clampRectToWorkArea(rect, wa application.Rect) application.Rect {
	if rect.Width > wa.Width {
		rect.Width = wa.Width
	}
	if rect.Height > wa.Height {
		rect.Height = wa.Height
	}
	rect.X = clampInt(rect.X, wa.X, wa.X+wa.Width-rect.Width)
	rect.Y = clampInt(rect.Y, wa.Y, wa.Y+wa.Height-rect.Height)
	return rect
}

// SaveMiniGeometry captures the current mini window bounds and persists them
// (debounced). Called from WindowDidMove / WindowDidResize hooks.
func (s *WindowService) SaveMiniGeometry() {
	w := s.miniWindow
	if w == nil {
		return
	}
	b := w.Bounds()
	state := s.loadMiniState()

	s.miniMu.Lock()
	state.X = b.X
	state.Y = b.Y
	state.Width = b.Width
	state.Height = b.Height
	state.HasPosition = true
	if s.miniSaveTmr != nil {
		s.miniSaveTmr.Stop()
	}
	s.miniSaveTmr = time.AfterFunc(miniSaveDebounce, s.persistMiniState)
	s.miniMu.Unlock()
}

// persistMiniState writes the cached state to the repository.
func (s *WindowService) persistMiniState() {
	if s.miniRepo == nil || s.miniState == nil {
		return
	}
	s.miniMu.Lock()
	snapshot := *s.miniState
	s.miniMu.Unlock()
	_ = s.miniRepo.Save(context.Background(), &snapshot)
}

// SetMiniAlwaysOnTop toggles always-on-top for the mini window and persists it.
// Called from the frontend pin button.
func (s *WindowService) SetMiniAlwaysOnTop(b bool) {
	if s.miniWindow != nil {
		s.miniWindow.SetAlwaysOnTop(b)
	}
	state := s.loadMiniState()
	s.miniMu.Lock()
	state.AlwaysOnTop = b
	s.miniMu.Unlock()
	s.persistMiniState()
}

// RestoreMiniAlwaysOnTop reapplies the persisted pin level to the open mini
// player. macOS may reset a floating NSWindow's level when the app is
// reactivated from the Dock, while the persisted pin preference remains true.
func (s *WindowService) RestoreMiniAlwaysOnTop() {
	if s.miniWindow == nil {
		return
	}
	s.miniWindow.SetAlwaysOnTop(s.loadMiniState().AlwaysOnTop)
}

// MiniState is the subset of mini player state the frontend needs on open.
type MiniState struct {
	AlwaysOnTop bool `json:"always_on_top"`
}

// GetMiniState returns the persisted mini player state for the frontend so the
// pin icon reflects the restored always-on-top setting.
func (s *WindowService) GetMiniState() MiniState {
	state := s.loadMiniState()
	return MiniState{AlwaysOnTop: state.AlwaysOnTop}
}

func clampInt(v, lo, hi int) int {
	if v < lo {
		return lo
	}
	if v > hi {
		return hi
	}
	return v
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
