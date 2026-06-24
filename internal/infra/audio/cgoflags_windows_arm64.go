//go:build windows && arm64

package audio

/*
#cgo CFLAGS: -I${SRCDIR}/ffmpeg_libs/include
#cgo LDFLAGS: -L${SRCDIR}/ffmpeg_libs/windows/arm64
#cgo LDFLAGS: -Wl,-Bstatic -lavformat -lavcodec -lswresample -lavutil -Wl,-Bdynamic -lmfplat -lmf -lmfuuid -lstrmiids -lws2_32 -lsecur32 -lbcrypt -lole32 -loleaut32 -luuid -lwinmm -lversion
// Windows/arm64 is built with the llvm-mingw toolchain (x86_64-hosted clang
// cross-compiler), whose C++ runtime is libc++ + libunwind, not libstdc++/libgcc.
// Statically link that runtime so the binary does not depend on libc++/libunwind/
// libwinpthread DLLs at runtime (not present on a clean Windows machine). The
// system libs above stay dynamic via -Wl,-Bdynamic; this re-enters static mode
// only for the implicit C++ runtime that smtc_windows.cpp (WinRT) pulls in.
#cgo LDFLAGS: -Wl,-Bstatic -lc++ -lc++abi -lunwind -lpthread -Wl,-Bdynamic
*/
import "C"
