//go:build windows

package wails

/*
#cgo LDFLAGS: -lcomctl32
#include "mini_player_aspect_windows.h"
*/
import "C"

import "github.com/wailsapp/wails/v3/pkg/application"

// LockMiniPlayerAspect subclasses the frameless mini-player HWND so WM_SIZING
// preserves the selected aspect ratio while the user drags any edge or corner.
func LockMiniPlayerAspect(w *application.WebviewWindow, expanded bool) {
	application.InvokeSync(func() {
		if nativeWindow := w.NativeWindow(); nativeWindow != nil {
			C.LockMiniPlayerAspect(nativeWindow, C.bool(expanded))
		}
	})
}

func SetMiniPlayerSizeNoAnimation(_ *application.WebviewWindow, _, _ int) bool { return false }
