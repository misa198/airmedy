//go:build windows

package audio

import "log/slog"

// newNowPlayingBackend returns the Windows OS media-controls backend, which
// bridges to the System Media Transport Controls (SMTC) via smtc_windows.cpp.
// The STA thread is started lazily on the first setupRemoteCommands call.
func newNowPlayingBackend(logger *slog.Logger) nowPlayingBackend {
	return &smtcBackend{logger: logger}
}
