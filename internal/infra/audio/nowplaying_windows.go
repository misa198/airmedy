//go:build windows

package audio

import "log/slog"

// newNowPlayingBackend returns the Windows OS media-controls backend.
//
// Full SMTC (System Media Transport Controls) integration requires the WinRT
// ISystemMediaTransportControls API, obtained per-window via the interop
// interface, plus a COM event sink for button presses. That is a sizeable,
// Windows-only effort that must be developed and tested on Windows. Until then
// this returns nil so the player still builds and runs; OS media controls are
// simply unavailable on Windows and all NowPlaying calls become no-ops.
func newNowPlayingBackend(logger *slog.Logger) nowPlayingBackend {
	logger.Info("SMTC: Windows OS media controls not yet implemented; skipping")
	return nil
}
