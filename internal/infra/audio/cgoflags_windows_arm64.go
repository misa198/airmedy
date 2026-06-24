//go:build windows && arm64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/windows/arm64
#cgo LDFLAGS: -Wl,-Bstatic -lavformat -lavcodec -lswresample -lavutil -Wl,-Bdynamic -lmfplat -lmf -lmfuuid -lstrmiids -lws2_32 -lsecur32 -lbcrypt -lole32 -loleaut32 -luuid -lwinmm -lversion
// Statically link the MinGW C++/pthread runtime so the binary does not depend on
// libstdc++-6.dll / libgcc_s_seh-1.dll / libwinpthread-1.dll at runtime (these are
// not present on a clean Windows machine). The system libs above are intentionally
// left dynamic via -Wl,-Bdynamic; this re-enters static mode only to resolve the
// implicit C++ runtime that smtc_windows.cpp (WinRT) pulls in. -static-libstdc++ /
// -static-libgcc alone do NOT work here because Go links via g++ and the trailing
// -Wl,-Bdynamic above leaves the linker in dynamic mode for the implicit -lstdc++.
#cgo LDFLAGS: -Wl,-Bstatic -lstdc++ -lpthread -Wl,-Bdynamic -static-libgcc
*/
import "C"
