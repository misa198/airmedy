//go:build windows

package wails

/*
#include "thumbbar_windows.h"
*/
import "C"
import (
	"log/slog"
	"sync"
	"unsafe"

	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
)

var (
	thumbMu   sync.Mutex
	thumbPrev func()
	thumbPP   func()
	thumbNext func()
)

//export goThumbBarPrev
func goThumbBarPrev() {
	thumbMu.Lock()
	cb := thumbPrev
	thumbMu.Unlock()
	if cb != nil {
		cb()
	}
}

//export goThumbBarPlayPause
func goThumbBarPlayPause() {
	thumbMu.Lock()
	cb := thumbPP
	thumbMu.Unlock()
	if cb != nil {
		cb()
	}
}

//export goThumbBarNext
func goThumbBarNext() {
	thumbMu.Lock()
	cb := thumbNext
	thumbMu.Unlock()
	if cb != nil {
		cb()
	}
}

// ThumbBarManager wires Windows Taskbar Thumbnail Toolbar buttons to the player.
type ThumbBarManager struct {
	playerService *player.PlayerService
}

func NewThumbBarManager(ps *player.PlayerService) *ThumbBarManager {
	return &ThumbBarManager{playerService: ps}
}

// Setup registers player callbacks and defers the HWND-dependent init until
// after wailsApp.Run() starts. The WindowFocus hook fires on a goroutine
// (not the message thread), so ThumbBarInit is dispatched via InvokeAsync
// to the Win32 message thread — the only thread where SetWindowSubclass and
// COM objects are valid.
func (m *ThumbBarManager) Setup(mainWindow *application.WebviewWindow) {
	// Callbacks invoked from the subclass WndProc (message thread).
	// Spawn goroutines to avoid blocking the message thread.
	thumbMu.Lock()
	thumbPrev = func() { go func() { _ = m.playerService.Previous() }() }
	thumbPP = func() { go func() { _ = m.playerService.TogglePause() }() }
	thumbNext = func() { go func() { _ = m.playerService.Next() }() }
	thumbMu.Unlock()

	m.playerService.AddStatusListener(func(s domain.PlayerStatus) {
		v := C.int(0)
		if s.PlaybackState == domain.PlaybackStatePlaying {
			v = 1
		}
		C.ThumbBarSetPlaying(v)
	})

	// WindowFocus fires on a goroutine after Run() starts the message loop.
	// Use InvokeAsync to move the actual init onto the message thread so that
	// SetWindowSubclass and ITaskbarList3 are created on the correct thread.
	var once sync.Once
	mainWindow.RegisterHook(events.Common.WindowFocus, func(_ *application.WindowEvent) {
		once.Do(func() {
			application.InvokeAsync(func() {
				hwnd := uintptr(mainWindow.NativeWindow())
				slog.Info("thumbbar: init on message thread", "hwnd", hwnd)
				if hwnd == 0 {
					slog.Warn("thumbbar: null HWND on message thread, skipping")
					return
				}
				playing := C.int(0)
				if m.playerService.GetStatus().PlaybackState == domain.PlaybackStatePlaying {
					playing = 1
				}
				C.ThumbBarInit(unsafe.Pointer(hwnd), playing) //nolint:unsafeptr
			})
		})
	})
}

// Stop removes the window subclass and releases all COM resources.
func (m *ThumbBarManager) Stop() {
	C.ThumbBarStop()
	slog.Debug("thumbbar: stopped")
}
