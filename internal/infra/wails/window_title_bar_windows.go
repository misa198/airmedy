//go:build windows

package wails

import (
	"syscall"
	"unsafe"

	"github.com/wailsapp/wails/v3/pkg/application"
)

var (
	modDwmapi         = syscall.NewLazyDLL("dwmapi.dll")
	procDwmSetWinAttr = modDwmapi.NewProc("DwmSetWindowAttribute")
)

const (
	dwmwaBorderColor  uint32 = 34
	dwmwaCaptionColor uint32 = 35
	dwmwaTextColor    uint32 = 36
)

func setTitleBarThemeImpl(w *application.WebviewWindow, theme string) {
	hwnd := uintptr(w.NativeWindow())
	bar, text, border := titleBarColors(theme)
	dwmSetAttr(hwnd, dwmwaBorderColor, border)
	dwmSetAttr(hwnd, dwmwaCaptionColor, bar)
	dwmSetAttr(hwnd, dwmwaTextColor, text)
}

func dwmSetAttr(hwnd uintptr, attr, color uint32) {
	procDwmSetWinAttr.Call(
		hwnd,
		uintptr(attr),
		uintptr(unsafe.Pointer(&color)),
		unsafe.Sizeof(color),
	)
}

// titleBarColors returns DWM border, caption, and text colors in 0x00BBGGRR format.
func titleBarColors(theme string) (bar, text, border uint32) {
	switch theme {
	case "light":
		return 0x00F5F4F4, 0x000A0A0A, 0x00E5E4E4 // #f4f4f5 bar, dark text, subtle border
	case "black":
		return 0x000A0A0A, 0x00FFFFFF, 0x000A0A0A // #0a0a0a bar + border, white text
	default: // "dark", "system"
		return 0x001B1818, 0x00FFFFFF, 0x001B1818 // #18181b bar + border, white text
	}
}
