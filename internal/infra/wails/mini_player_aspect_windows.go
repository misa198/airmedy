//go:build windows

package wails

/*
#cgo LDFLAGS: -lcomctl32
#include "mini_player_aspect_windows.h"
*/
import "C"

import "github.com/wailsapp/wails/v3/pkg/application"

// LockMiniPlayerSquare subclasses the frameless mini-player HWND so WM_SIZING
// preserves a 1:1 ratio while the user drags any edge or corner.
func LockMiniPlayerSquare(w *application.WebviewWindow) {
	application.InvokeSync(func() {
		if nativeWindow := w.NativeWindow(); nativeWindow != nil {
			C.LockMiniPlayerSquare(nativeWindow)
		}
	})
}
