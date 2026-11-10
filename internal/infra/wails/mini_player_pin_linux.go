//go:build linux && cgo

package wails

/*
#cgo pkg-config: gtk4
#include <gtk/gtk.h>
#include <gdk/x11/gdkx.h>

static bool mini_always_on_top_supported(void *window) {
	GdkDisplay *display = gtk_widget_get_display(GTK_WIDGET(window));
	return display != NULL && GDK_IS_X11_DISPLAY(display);
}
*/
import "C"

import "github.com/wailsapp/wails/v3/pkg/application"

func supportsMiniAlwaysOnTop(w *application.WebviewWindow) bool {
	if w == nil {
		return false
	}

	supported := false
	application.InvokeSync(func() {
		if nativeWindow := w.NativeWindow(); nativeWindow != nil {
			supported = bool(C.mini_always_on_top_supported(nativeWindow))
		}
	})
	return supported
}
