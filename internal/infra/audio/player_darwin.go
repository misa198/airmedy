//go:build darwin

package audio

/*
#cgo CFLAGS: -x objective-c -fobjc-arc -fmodules
#cgo LDFLAGS: -framework Foundation -framework AppKit -framework AVFoundation -framework CoreMedia -framework MediaPlayer
#include <stdlib.h>

void* InitPlayer();
void DestroyPlayer(void* player);
void PlayPlayer(void* player);
void PausePlayer(void* player);
void StopPlayer(void* player);
void SeekPlayer(void* player, double seconds);
void SetVolumePlayer(void* player, float volume);
void LoadPlayer(void* player, const char* url);
double GetCurrentTimePlayer(void* player);

void SetupRemoteCommandCenter(void* player);
void UpdateNowPlayingInfo(void* player, const char* title, const char* artist,
    const char* album, double duration, double position, const char* artworkPath);
void ClearNowPlayingInfo(void* player);
void UpdateNowPlayingPosition(void* player, double position);
void SetEQBand(void* player, int index, double freq, double gain, double bandwidth);
void SetEQEnabled(void* player, int enabled);
void EnqueueNextPlayer(void* player, const char* path);
void ClearEnqueuedPlayer(void* player);
*/
import "C"
import (
	"log/slog"
	"sync"
	"unsafe"

	"airmedy/internal/domain"
)

var (
	onTrackEndCallback func()
	callbackMutex      sync.Mutex

	onRemotePlayCallback     func()
	onRemotePauseCallback    func()
	onRemoteNextCallback     func()
	onRemotePreviousCallback func()
	onRemoteSeekCallback     func(float64)
	remoteCallbackMu         sync.Mutex
)

//export goHandleTrackEnd
func goHandleTrackEnd() {
	callbackMutex.Lock()
	defer callbackMutex.Unlock()
	if onTrackEndCallback != nil {
		onTrackEndCallback()
	}
}

//export goHandleRemotePlay
func goHandleRemotePlay() {
	remoteCallbackMu.Lock()
	defer remoteCallbackMu.Unlock()
	if onRemotePlayCallback != nil {
		onRemotePlayCallback()
	}
}

//export goHandleRemotePause
func goHandleRemotePause() {
	remoteCallbackMu.Lock()
	defer remoteCallbackMu.Unlock()
	if onRemotePauseCallback != nil {
		onRemotePauseCallback()
	}
}

//export goHandleRemoteNext
func goHandleRemoteNext() {
	remoteCallbackMu.Lock()
	defer remoteCallbackMu.Unlock()
	if onRemoteNextCallback != nil {
		onRemoteNextCallback()
	}
}

//export goHandleRemotePrevious
func goHandleRemotePrevious() {
	remoteCallbackMu.Lock()
	defer remoteCallbackMu.Unlock()
	if onRemotePreviousCallback != nil {
		onRemotePreviousCallback()
	}
}

//export goHandleRemoteSeek
func goHandleRemoteSeek(position C.double) {
	remoteCallbackMu.Lock()
	defer remoteCallbackMu.Unlock()
	if onRemoteSeekCallback != nil {
		onRemoteSeekCallback(float64(position))
	}
}

// DarwinPlayer implements domain.AudioPlayer and domain.NowPlayingController using SFBAudioEngine on macOS.
type DarwinPlayer struct {
	logger        *slog.Logger
	playerPointer unsafe.Pointer
	status        domain.PlayerStatus
}

func NewPlayer(logger *slog.Logger) domain.AudioPlayer {
	return &DarwinPlayer{
		logger:        logger,
		playerPointer: C.InitPlayer(),
		status: domain.PlayerStatus{
			PlaybackState: domain.PlaybackStateStopped,
			Volume:        1.0,
		},
	}
}

func (p *DarwinPlayer) Play() error {
	C.PlayPlayer(p.playerPointer)
	p.status.PlaybackState = domain.PlaybackStatePlaying
	return nil
}

func (p *DarwinPlayer) Pause() error {
	C.PausePlayer(p.playerPointer)
	p.status.PlaybackState = domain.PlaybackStatePaused
	return nil
}

func (p *DarwinPlayer) Stop() error {
	C.StopPlayer(p.playerPointer)
	p.status.PlaybackState = domain.PlaybackStateStopped
	return nil
}

func (p *DarwinPlayer) Seek(position float64) error {
	C.SeekPlayer(p.playerPointer, C.double(position))
	p.status.Position = position
	return nil
}

func (p *DarwinPlayer) SetVolume(volume float64) error {
	C.SetVolumePlayer(p.playerPointer, C.float(volume))
	p.status.Volume = volume
	return nil
}

func (p *DarwinPlayer) SetMuted(muted bool) error {
	if muted {
		C.SetVolumePlayer(p.playerPointer, 0)
	} else {
		C.SetVolumePlayer(p.playerPointer, C.float(p.status.Volume))
	}
	p.status.Muted = muted
	return nil
}

func (p *DarwinPlayer) Load(track *domain.TrackDTO) error {
	cUrl := C.CString(track.Path)
	defer C.free(unsafe.Pointer(cUrl))
	C.LoadPlayer(p.playerPointer, cUrl)

	p.status.TrackID = track.ID
	p.status.Duration = float64(track.Duration)
	p.status.Position = 0
	return nil
}

func (p *DarwinPlayer) Unload() error {
	p.Stop()
	p.status.TrackID = ""
	return nil
}

func (p *DarwinPlayer) GetStatus() domain.PlayerStatus {
	p.status.Position = float64(C.GetCurrentTimePlayer(p.playerPointer))
	return p.status
}

func (p *DarwinPlayer) OnTrackEnd(callback func()) {
	callbackMutex.Lock()
	defer callbackMutex.Unlock()
	onTrackEndCallback = callback
}

// --- EQController ---

func (p *DarwinPlayer) SetEQBand(index int, frequency, gain, bandwidth float64) error {
	C.SetEQBand(p.playerPointer, C.int(index), C.double(frequency), C.double(gain), C.double(bandwidth))
	return nil
}

func (p *DarwinPlayer) SetEQEnabled(enabled bool) error {
	val := 0
	if enabled {
		val = 1
	}
	C.SetEQEnabled(p.playerPointer, C.int(val))
	return nil
}

// --- GaplessPlayer ---

func (p *DarwinPlayer) EnqueueNext(track *domain.TrackDTO) error {
	cPath := C.CString(track.Path)
	defer C.free(unsafe.Pointer(cPath))
	C.EnqueueNextPlayer(p.playerPointer, cPath)
	return nil
}

func (p *DarwinPlayer) StartPreloaded(track *domain.TrackDTO) error {
	// SFBAudioEngine auto-transitioned; update Go-side status tracking.
	p.status.TrackID = track.ID
	p.status.Duration = float64(track.Duration)
	p.status.Position = 0
	return nil
}

func (p *DarwinPlayer) AutoTransitions() bool {
	return true
}

func (p *DarwinPlayer) ClearEnqueued() {
	C.ClearEnqueuedPlayer(p.playerPointer)
}

// --- NowPlayingController ---

func (p *DarwinPlayer) SetupRemoteCommands() {
	C.SetupRemoteCommandCenter(p.playerPointer)
}

func (p *DarwinPlayer) SetRemoteCallbacks(play, pause, next, previous func(), seek func(float64)) {
	remoteCallbackMu.Lock()
	defer remoteCallbackMu.Unlock()
	onRemotePlayCallback = play
	onRemotePauseCallback = pause
	onRemoteNextCallback = next
	onRemotePreviousCallback = previous
	onRemoteSeekCallback = seek
}

func (p *DarwinPlayer) UpdateNowPlaying(track *domain.TrackDTO, position float64, artworkPath string) {
	title := C.CString(track.Title)
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

	C.UpdateNowPlayingInfo(
		p.playerPointer,
		title,
		cArtist,
		cAlbum,
		C.double(float64(track.Duration)),
		C.double(position),
		cArtwork,
	)
}

func (p *DarwinPlayer) ClearNowPlaying() {
	C.ClearNowPlayingInfo(p.playerPointer)
}

func (p *DarwinPlayer) Close() {
	C.DestroyPlayer(p.playerPointer)
	p.playerPointer = nil
}

func (p *DarwinPlayer) UpdateNowPlayingPosition(position float64) {
	C.UpdateNowPlayingPosition(p.playerPointer, C.double(position))
}
