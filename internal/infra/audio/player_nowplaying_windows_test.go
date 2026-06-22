//go:build windows

package audio

import "airmedy/internal/domain"

// Compile-time assertions: on Windows, *MiniAudioPlayer must satisfy both the
// OS Now Playing controller and the optional play/pause-state interfaces so
// PlayerService wires up SMTC. If these break, Windows media integration is lost.
var (
	_ domain.NowPlayingController    = (*MiniAudioPlayer)(nil)
	_ domain.NowPlayingPlaybackState = (*MiniAudioPlayer)(nil)
)
