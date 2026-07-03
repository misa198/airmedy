//go:build linux && arm64

package audio

// libkeyfinder (musical key/mode detection) + its FFTW3 dependency, for the
// in-process analyzer (analyzer.go / keyfinder_bridge.cpp).

/*
#cgo CPPFLAGS: -I${SRCDIR}/keyfinder_libs/include -I${SRCDIR}/fftw3_libs/include
#cgo LDFLAGS: -L${SRCDIR}/keyfinder_libs/linux/arm64 -L${SRCDIR}/fftw3_libs/linux/arm64
#cgo LDFLAGS: -lkeyfinder -lfftw3
*/
import "C"
