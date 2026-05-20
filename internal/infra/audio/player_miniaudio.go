//go:build windows || linux

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/miniaudio -I${SRCDIR} -std=c11


#include "miniaudio_wrapper.h"
#include <stdlib.h>

extern void goMiniAudioTrackEnd(void* userdata);
*/
import "C"
import (
	"fmt"
	"log/slog"
	"sync"
	"unsafe"

	"airmedy/internal/domain"
)

var (
	miniAudioEndCallback func()
	miniAudioEndMu       sync.Mutex
)

//export goMiniAudioTrackEnd
func goMiniAudioTrackEnd(_ unsafe.Pointer) {
	miniAudioEndMu.Lock()
	cb := miniAudioEndCallback
	miniAudioEndMu.Unlock()
	if cb != nil {
		// MUST run in a goroutine. Miniaudio documentation warns not to
		// uninitialize or change sound state from within the callback thread.
		// Since HandleTrackEnd calls Load() -> ma_sound_uninit(), it would deadlock.
		go cb()
	}
}

type MiniAudioPlayer struct {
	logger *slog.Logger
	ptr    unsafe.Pointer // *MaPlayer
	status domain.PlayerStatus
}

func NewPlayer(logger *slog.Logger) domain.AudioPlayer {
	p := C.ma_player_create()
	if p == nil {
		logger.Error("MiniAudioPlayer: failed to initialise audio engine")
	}
	return &MiniAudioPlayer{
		logger: logger,
		ptr:    unsafe.Pointer(p),
		status: domain.PlayerStatus{
			PlaybackState: domain.PlaybackStateStopped,
			Volume:        1.0,
		},
	}
}

func (p *MiniAudioPlayer) Play() error {
	if rc := C.ma_player_play((*C.MaPlayer)(p.ptr)); rc != 0 {
		return fmt.Errorf("ma_player_play failed: %d", rc)
	}
	p.status.PlaybackState = domain.PlaybackStatePlaying
	return nil
}

func (p *MiniAudioPlayer) Pause() error {
	if rc := C.ma_player_pause((*C.MaPlayer)(p.ptr)); rc != 0 {
		return fmt.Errorf("ma_player_pause failed: %d", rc)
	}
	p.status.PlaybackState = domain.PlaybackStatePaused
	return nil
}

func (p *MiniAudioPlayer) Stop() error {
	if rc := C.ma_player_stop((*C.MaPlayer)(p.ptr)); rc != 0 {
		return fmt.Errorf("ma_player_stop failed: %d", rc)
	}
	p.status.PlaybackState = domain.PlaybackStateStopped
	p.status.Position = 0
	return nil
}

func (p *MiniAudioPlayer) Seek(position float64) error {
	if rc := C.ma_player_seek((*C.MaPlayer)(p.ptr), C.double(position)); rc != 0 {
		return fmt.Errorf("ma_player_seek failed: %d", rc)
	}
	p.status.Position = position
	return nil
}

func (p *MiniAudioPlayer) SetVolume(volume float64) error {
	if rc := C.ma_player_set_volume((*C.MaPlayer)(p.ptr), C.float(volume)); rc != 0 {
		return fmt.Errorf("ma_player_set_volume failed: %d", rc)
	}
	p.status.Volume = volume
	return nil
}

func (p *MiniAudioPlayer) SetMuted(muted bool) error {
	var vol C.float
	if muted {
		vol = 0.0
	} else {
		vol = C.float(p.status.Volume)
	}
	if rc := C.ma_player_set_volume((*C.MaPlayer)(p.ptr), vol); rc != 0 {
		return fmt.Errorf("ma_player_set_volume failed: %d", rc)
	}
	p.status.Muted = muted
	return nil
}

func (p *MiniAudioPlayer) Load(track *domain.TrackDTO) error {
	cPath := C.CString(track.Path)
	defer C.free(unsafe.Pointer(cPath))

	if rc := C.ma_player_load((*C.MaPlayer)(p.ptr), cPath); rc != 0 {
		return fmt.Errorf("ma_player_load failed: %d (path: %s)", rc, track.Path)
	}

	p.status.TrackID = track.ID
	p.status.Duration = float64(C.ma_player_get_length((*C.MaPlayer)(p.ptr)))
	p.status.Position = 0
	return nil
}

func (p *MiniAudioPlayer) Unload() error {
	if err := p.Stop(); err != nil {
		p.logger.Warn("MiniAudioPlayer: stop during unload", "err", err)
	}
	C.ma_player_unload((*C.MaPlayer)(p.ptr))
	p.status.TrackID = ""
	p.status.Duration = 0
	p.status.Position = 0
	return nil
}

func (p *MiniAudioPlayer) GetStatus() domain.PlayerStatus {
	p.status.Position = float64(C.ma_player_get_cursor((*C.MaPlayer)(p.ptr)))
	return p.status
}

func (p *MiniAudioPlayer) OnTrackEnd(callback func()) {
	miniAudioEndMu.Lock()
	miniAudioEndCallback = callback
	miniAudioEndMu.Unlock()
	C.ma_player_set_end_callback(
		(*C.MaPlayer)(p.ptr),
		C.MaEndCallback(C.goMiniAudioTrackEnd),
		nil,
	)
}

func (p *MiniAudioPlayer) SetEQBand(index int, frequency, gain, bandwidth float64) error {
	if rc := C.ma_player_set_eq_band((*C.MaPlayer)(p.ptr), C.int(index), C.float(frequency), C.float(gain), C.float(bandwidth)); rc != 0 {
		return fmt.Errorf("ma_player_set_eq_band failed: %d", rc)
	}
	return nil
}

func (p *MiniAudioPlayer) SetEQEnabled(enabled bool) error {
	e := 0
	if enabled {
		e = 1
	}
	if rc := C.ma_player_set_eq_enabled((*C.MaPlayer)(p.ptr), C.int(e)); rc != 0 {
		return fmt.Errorf("ma_player_set_eq_enabled failed: %d", rc)
	}
	return nil
}

// --- GaplessPlayer ---

func (p *MiniAudioPlayer) EnqueueNext(track *domain.TrackDTO) error {
	cPath := C.CString(track.Path)
	defer C.free(unsafe.Pointer(cPath))
	if rc := C.ma_player_preload_next((*C.MaPlayer)(p.ptr), cPath); rc != 0 {
		return fmt.Errorf("ma_player_preload_next failed: %d (path: %s)", rc, track.Path)
	}
	return nil
}

func (p *MiniAudioPlayer) StartPreloaded(track *domain.TrackDTO) error {
	if rc := C.ma_player_start_preloaded((*C.MaPlayer)(p.ptr)); rc != 0 {
		return fmt.Errorf("ma_player_start_preloaded failed: %d", rc)
	}
	p.status.TrackID = track.ID
	p.status.Duration = float64(C.ma_player_get_length((*C.MaPlayer)(p.ptr)))
	p.status.Position = 0
	p.status.PlaybackState = domain.PlaybackStatePlaying
	return nil
}

func (p *MiniAudioPlayer) AutoTransitions() bool {
	return false
}

func (p *MiniAudioPlayer) ClearEnqueued() {
	C.ma_player_clear_preloaded((*C.MaPlayer)(p.ptr))
}
