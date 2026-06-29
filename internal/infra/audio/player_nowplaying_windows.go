//go:build windows

package audio

/*
#include "smtc_windows.h"
#include <stdlib.h>
*/
import "C"
import (
	"log/slog"
	"path/filepath"
	"strings"
	"sync"
	"unsafe"

	"airmedy/internal/domain"
)

// smtcBackend implements nowPlayingBackend on Windows by bridging to the System
// Media Transport Controls (SMTC) backend in smtc_windows.cpp. PlayerService
// drives it through *MiniAudioPlayer's nowPlayingBackend delegation (nowplaying.go).
type smtcBackend struct {
	logger  *slog.Logger
	started bool
}

var (
	winNPPlay     func()
	winNPPause    func()
	winNPNext     func()
	winNPPrevious func()
	winNPSeek     func(float64)
	winNPMu       sync.Mutex
)

//export goWinNowPlayingPlay
func goWinNowPlayingPlay() {
	slog.Debug("smtc: remote play")
	winNPMu.Lock()
	cb := winNPPlay
	winNPMu.Unlock()
	if cb != nil {
		cb()
	}
}

//export goWinNowPlayingPause
func goWinNowPlayingPause() {
	slog.Debug("smtc: remote pause")
	winNPMu.Lock()
	cb := winNPPause
	winNPMu.Unlock()
	if cb != nil {
		cb()
	}
}

//export goWinNowPlayingNext
func goWinNowPlayingNext() {
	slog.Debug("smtc: remote next")
	winNPMu.Lock()
	cb := winNPNext
	winNPMu.Unlock()
	if cb != nil {
		cb()
	}
}

//export goWinNowPlayingPrevious
func goWinNowPlayingPrevious() {
	slog.Debug("smtc: remote previous")
	winNPMu.Lock()
	cb := winNPPrevious
	winNPMu.Unlock()
	if cb != nil {
		cb()
	}
}

//export goWinNowPlayingSeek
func goWinNowPlayingSeek(position C.double) {
	pos := float64(position)
	slog.Debug("smtc: remote seek", "position", pos)
	winNPMu.Lock()
	cb := winNPSeek
	winNPMu.Unlock()
	if cb != nil {
		cb(pos)
	}
}

// --- nowPlayingBackend ---

func (b *smtcBackend) setupRemoteCommands() {
	if b.started {
		return
	}
	b.started = true
	b.logger.Debug("smtc: starting STA thread")
	C.SmtcStart()
	b.logger.Debug("smtc: STA thread ready")
}

func (b *smtcBackend) setRemoteCallbacks(play, pause, next, previous func(), seek func(float64)) {
	winNPMu.Lock()
	defer winNPMu.Unlock()
	winNPPlay = play
	winNPPause = pause
	winNPNext = next
	winNPPrevious = previous
	winNPSeek = seek
}

func (b *smtcBackend) updateNowPlaying(track *domain.TrackDTO, position float64, artworkPath string) {
	if track == nil {
		return
	}

	// SMTC silently shows no card when the title is empty, so fall back to the
	// file name (sans extension) and finally a constant for untagged tracks.
	titleText := track.Title
	if titleText == "" && track.Path != "" {
		base := filepath.Base(track.Path)
		titleText = strings.TrimSuffix(base, filepath.Ext(base))
	}
	if titleText == "" {
		titleText = "Unknown Track"
	}

	b.logger.Debug("smtc: update now playing",
		"title", titleText,
		"duration", track.Duration,
		"position", position,
		"artwork", artworkPath,
	)
	title := C.CString(titleText)
	defer C.free(unsafe.Pointer(title))

	artist := ""
	if len(track.Artists) > 0 {
		artist = track.Artists[0].Name
	}
	cArtist := C.CString(artist)
	defer C.free(unsafe.Pointer(cArtist))

	albumTitle := ""
	if track.Album != nil {
		albumTitle = track.Album.Title
	}
	cAlbum := C.CString(albumTitle)
	defer C.free(unsafe.Pointer(cAlbum))

	cArtwork := C.CString(artworkPath)
	defer C.free(unsafe.Pointer(cArtwork))

	C.SmtcUpdate(
		title,
		cArtist,
		cAlbum,
		C.double(float64(track.Duration)),
		C.double(position),
		cArtwork,
	)
}

func (b *smtcBackend) updateNowPlayingPosition(position float64) {
	C.SmtcUpdatePosition(C.double(position))
}

func (b *smtcBackend) setPlaybackState(state domain.PlaybackState) {
	b.logger.Debug("smtc: playback state", "state", state)
	v := C.int(0)
	if state == domain.PlaybackStatePlaying {
		v = 1
	}
	C.SmtcSetPlaybackStatus(v)
}

func (b *smtcBackend) clearNowPlaying() {
	b.logger.Debug("smtc: clear")
	C.SmtcClear()
}

// winNPActivate is called (in a goroutine) when Windows activates the SMTC
// window — i.e., the user clicked "Now Playing" in the media flyout.
var winNPActivate func()

//export goWinNowPlayingActivate
func goWinNowPlayingActivate() {
	slog.Debug("smtc: app activate requested")
	winNPMu.Lock()
	cb := winNPActivate
	winNPMu.Unlock()
	if cb != nil {
		go cb()
	}
}

func (b *smtcBackend) setActivateCallback(cb func()) {
	winNPMu.Lock()
	winNPActivate = cb
	winNPMu.Unlock()
}

// close stops the SMTC backend. Safe to call when SMTC never started.
func (b *smtcBackend) close() {
	b.logger.Debug("smtc: stopping")
	C.SmtcStop()
	b.logger.Debug("smtc: stopped")
}
