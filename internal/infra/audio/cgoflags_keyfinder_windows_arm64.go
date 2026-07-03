//go:build windows && arm64

package audio

// libkeyfinder (musical key/mode detection) + its FFTW3 dependency, for the
// in-process analyzer (analyzer.go / keyfinder_bridge.cpp). Built with
// llvm-mingw like the rest of this arch's native deps (see
// cgoflags_windows_arm64.go) — libc++/libunwind runtime already linked there.

/*
#cgo CPPFLAGS: -I${SRCDIR}/keyfinder_libs/include -I${SRCDIR}/fftw3_libs/include
#cgo LDFLAGS: -L${SRCDIR}/keyfinder_libs/windows/arm64 -L${SRCDIR}/fftw3_libs/windows/arm64
#cgo LDFLAGS: -Wl,-Bstatic -Wl,--start-group -lkeyfinder -lfftw3 -Wl,--end-group -Wl,-Bdynamic
*/
import "C"
