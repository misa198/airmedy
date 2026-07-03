//go:build windows && amd64

package audio

// libkeyfinder (musical key/mode detection) + its FFTW3 dependency, for the
// in-process analyzer (analyzer.go / keyfinder_bridge.cpp). C++ runtime linking
// follows the same -Wl,-Bstatic/-Bdynamic dance as cgoflags_windows_amd64.go
// (smtc_windows.cpp) — both C++ translation units share one implicit -lstdc++.

/*
#cgo CPPFLAGS: -I${SRCDIR}/keyfinder_libs/include -I${SRCDIR}/fftw3_libs/include
#cgo LDFLAGS: -L${SRCDIR}/keyfinder_libs/windows/amd64 -L${SRCDIR}/fftw3_libs/windows/amd64
#cgo LDFLAGS: -Wl,-Bstatic -lkeyfinder -lfftw3 -Wl,-Bdynamic
*/
import "C"
