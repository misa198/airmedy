//go:build darwin

package wails

/*
#cgo LDFLAGS: -framework AppKit
#include "mini_player_aspect_darwin.h"
*/
import "C"

import "github.com/wailsapp/wails/v3/pkg/application"

// LockMiniPlayerAspect delegates interactive resizing to AppKit, avoiding
// frontend resize feedback loops.
func LockMiniPlayerAspect(w *application.WebviewWindow, expanded bool) {
	application.InvokeSync(func() {
		if nativeWindow := w.NativeWindow(); nativeWindow != nil {
			C.LockMiniPlayerAspect(nativeWindow, C.bool(expanded))
		}
	})
}

// SetMiniPlayerSizeNoAnimation bypasses Wails' animated SetBounds path on macOS.
func SetMiniPlayerSizeNoAnimation(w *application.WebviewWindow, width, height int) bool {
	handled := false
	application.InvokeSync(func() {
		if nativeWindow := w.NativeWindow(); nativeWindow != nil {
			C.SetMiniPlayerSizeNoAnimation(nativeWindow, C.int(width), C.int(height))
			handled = true
		}
	})
	return handled
}
