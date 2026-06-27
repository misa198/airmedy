//go:build windows && amd64

package wails

// Statically link the MinGW C++/pthread runtime so thumbbar_windows.cpp (C++)
// does not require libstdc++-6.dll / libgcc_s_seh-1.dll at runtime.

/*
#cgo LDFLAGS: -Wl,-Bstatic -lstdc++ -lpthread -Wl,-Bdynamic -static-libgcc
*/
import "C"
