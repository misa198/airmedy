//go:build darwin && amd64

package audio

// FFmpeg static libs for the in-process loudness analyzer (analyzer.go).
// Kept separate from cgoflags_darwin_amd64.go (SFBAudioEngine playback) — cgo
// merges all #cgo directives in the package, so both link side by side. This is
// the deliberate, accepted darwin re-link onto ffmpeg noted in the analysis plan.

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include -I${SRCDIR}/aubio_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/darwin/amd64 -L${SRCDIR}/aubio_libs/darwin/amd64
#cgo LDFLAGS: -lavfilter -lavformat -lavcodec -lswresample -lavutil -laubio
#cgo LDFLAGS: -lz -lbz2 -liconv -lm
#cgo LDFLAGS: -framework CoreFoundation -framework CoreMedia -framework CoreVideo -framework VideoToolbox -framework AudioToolbox -framework Security
*/
import "C"
