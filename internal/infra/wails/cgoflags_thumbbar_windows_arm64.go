//go:build windows && arm64

package wails

// llvm-mingw (arm64) uses libc++/libunwind instead of libstdc++/libgcc.
// Suppress the redundant dllexport attribute warning clang emits for //export.

/*
#cgo CFLAGS: -Wno-dll-attribute-on-redeclaration
#cgo LDFLAGS: -Wl,-Bstatic -lc++ -lc++abi -lunwind -lpthread -Wl,-Bdynamic
*/
import "C"
