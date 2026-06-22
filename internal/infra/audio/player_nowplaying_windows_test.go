//go:build windows

package audio

import "airmedy/internal/domain"

// Compile-time assertion: on Windows, *MiniAudioPlayer must satisfy the OS Now
// Playing controller so PlayerService wires up SMTC. If this breaks, Windows
// media integration is lost. Playback play/pause state is pushed through the
// nowPlayingBackend (setPlaybackState), driven by Play/Pause/Stop, so the player
// no longer implements domain.NowPlayingPlaybackState directly.
var _ domain.NowPlayingController = (*MiniAudioPlayer)(nil)
