//go:build linux && cgo

package wails

/*
#cgo pkg-config: gtk4 x11
#include "mini_player_aspect_linux.h"
*/
import "C"

import "github.com/wailsapp/wails/v3/pkg/application"

// LockMiniPlayerAspect sets X11's native aspect hint. Wayland has no equivalent
// interactive-resize constraint, so this is deliberately a no-op there.
func LockMiniPlayerAspect(w *application.WebviewWindow, expanded bool) {
	application.InvokeSync(func() {
		if nativeWindow := w.NativeWindow(); nativeWindow != nil {
			C.LockMiniPlayerAspect(nativeWindow, C.bool(expanded))
		}
	})
}

func SetMiniPlayerSizeNoAnimation(_ *application.WebviewWindow, _, _ int) bool { return false }
