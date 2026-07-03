//go:build darwin && amd64

package audio

// libkeyfinder (musical key/mode detection) + its FFTW3 dependency, for the
// in-process analyzer (analyzer.go / keyfinder_bridge.cpp). Kept separate from
// cgoflags_ffmpeg_darwin_amd64.go — cgo merges all #cgo directives package-wide.

/*
#cgo CPPFLAGS: -I${SRCDIR}/keyfinder_libs/include -I${SRCDIR}/fftw3_libs/include
#cgo LDFLAGS: -L${SRCDIR}/keyfinder_libs/darwin/amd64 -L${SRCDIR}/fftw3_libs/darwin/amd64
#cgo LDFLAGS: -lkeyfinder -lfftw3
*/
import "C"
