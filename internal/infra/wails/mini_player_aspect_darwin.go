//go:build darwin

package wails

/*
#cgo LDFLAGS: -framework AppKit
#include "mini_player_aspect_darwin.h"
*/
import "C"

import "github.com/wailsapp/wails/v3/pkg/application"

// LockMiniPlayerSquare delegates interactive resizing to AppKit, avoiding
// frontend resize feedback loops.
func LockMiniPlayerSquare(w *application.WebviewWindow) {
	application.InvokeSync(func() {
		if nativeWindow := w.NativeWindow(); nativeWindow != nil {
			C.LockMiniPlayerSquare(nativeWindow)
		}
	})
}
