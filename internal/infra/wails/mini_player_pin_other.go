//go:build !linux

package wails

import "github.com/wailsapp/wails/v3/pkg/application"

func supportsMiniAlwaysOnTop(_ *application.WebviewWindow) bool { return true }
