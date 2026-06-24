//go:build !windows

package wails

import "github.com/wailsapp/wails/v3/pkg/application"

func setTitleBarThemeImpl(_ *application.WebviewWindow, _ string) {}
