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

// This file makes *MiniAudioPlayer satisfy domain.NowPlayingController and
// domain.NowPlayingPlaybackState on Windows by bridging to the System Media
// Transport Controls (SMTC) backend in smtc_windows.cpp. It is Windows-only, so
// the Linux build of MiniAudioPlayer remains a non-NowPlayingController and
// PlayerService leaves nowPlaying nil there.

var (
	winNPPlay     func()
	winNPPause    func()
	winNPNext     func()
	winNPPrevious func()
	winNPSeek     func(float64)
	winNPMu       sync.Mutex

	smtcStarted bool
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

// --- NowPlayingController ---

func (p *MiniAudioPlayer) SetupRemoteCommands() {
	if smtcStarted {
		return
	}
	smtcStarted = true
	p.logger.Debug("smtc: starting STA thread")
	C.SmtcStart()
	p.logger.Debug("smtc: STA thread ready")
}

func (p *MiniAudioPlayer) SetRemoteCallbacks(play, pause, next, previous func(), seek func(float64)) {
	winNPMu.Lock()
	defer winNPMu.Unlock()
	winNPPlay = play
	winNPPause = pause
	winNPNext = next
	winNPPrevious = previous
	winNPSeek = seek
}

func (p *MiniAudioPlayer) UpdateNowPlaying(track *domain.TrackDTO, position float64, artworkPath string) {
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

	p.logger.Debug("smtc: update now playing",
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

func (p *MiniAudioPlayer) UpdateNowPlayingPosition(position float64) {
	C.SmtcUpdatePosition(C.double(position))
}

func (p *MiniAudioPlayer) ClearNowPlaying() {
	p.logger.Debug("smtc: clear")
	C.SmtcClear()
}

// --- NowPlayingPlaybackState ---

func (p *MiniAudioPlayer) SetNowPlayingPlaybackState(playing bool) {
	p.logger.Debug("smtc: playback state", "playing", playing)
	v := C.int(0)
	if playing {
		v = 1
	}
	C.SmtcSetPlaybackStatus(v)
}

// --- Teardown ---

// Close stops the SMTC backend. Invoked by PlayerService on shutdown via the
// optional interface{ Close() } assertion. Safe to call when SMTC never started.
func (p *MiniAudioPlayer) Close() {
	p.logger.Debug("smtc: stopping")
	C.SmtcStop()
	p.logger.Debug("smtc: stopped")
}
