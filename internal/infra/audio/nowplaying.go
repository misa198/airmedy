//go:build windows || linux

package audio

import "airmedy/internal/domain"

// nowPlayingBackend is the platform-specific OS "Now Playing" / media-controls
// integration. Linux implements it over MPRIS (D-Bus); Windows over SMTC.
// newNowPlayingBackend is provided per-platform and may return nil when the OS
// integration is unavailable, in which case all calls become no-ops.
type nowPlayingBackend interface {
	setupRemoteCommands()
	setRemoteCallbacks(play, pause, next, previous func(), seek func(float64))
	updateNowPlaying(track *domain.TrackDTO, position float64, artworkPath string)
	updateNowPlayingPosition(position float64)
	setPlaybackState(state domain.PlaybackState)
	clearNowPlaying()
	close()
	// setActivateCallback registers a function to call when the OS requests
	// app activation via the media session (e.g. "Now Playing" card click on
	// Windows). The callback should bring the appropriate window to front.
	// No-op on platforms that don't use it.
	setActivateCallback(cb func())
}

// MiniAudioPlayer satisfies domain.NowPlayingController by delegating to the
// platform backend. The service type-asserts for this interface, so the methods
// must exist on every windows/linux build even when np is nil.

func (p *MiniAudioPlayer) SetupRemoteCommands() {
	if p.np != nil {
		p.np.setupRemoteCommands()
	}
}

func (p *MiniAudioPlayer) SetRemoteCallbacks(play, pause, next, previous func(), seek func(float64)) {
	if p.np != nil {
		p.np.setRemoteCallbacks(play, pause, next, previous, seek)
	}
}

func (p *MiniAudioPlayer) UpdateNowPlaying(track *domain.TrackDTO, position float64, artworkPath string) {
	if p.np != nil {
		p.np.updateNowPlaying(track, position, artworkPath)
	}
}

func (p *MiniAudioPlayer) UpdateNowPlayingPosition(position float64) {
	if p.np != nil {
		p.np.updateNowPlayingPosition(position)
	}
}

func (p *MiniAudioPlayer) ClearNowPlaying() {
	if p.np != nil {
		p.np.clearNowPlaying()
	}
}

func (p *MiniAudioPlayer) SetActivateCallback(cb func()) {
	if p.np != nil {
		p.np.setActivateCallback(cb)
	}
}
