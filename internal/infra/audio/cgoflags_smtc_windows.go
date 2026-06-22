//go:build windows

package audio

// Linker flags for the Windows SMTC backend (smtc_windows.cpp). Kept in a
// windows-wide file (not the amd64-only cgoflags) so arm64 builds also link
// these WinRT/shell libraries.
//
//   -lruntimeobject : RoInitialize / RoGetActivationFactory / RoActivateInstance
//                     and the WinRT HSTRING helpers (WindowsCreateString, ...).
//   -lshcore        : CreateRandomAccessStreamOnFile (artwork file -> WinRT stream).
//   -lshell32       : SetCurrentProcessExplicitAppUserModelID.
//
// ole32 / oleaut32 / uuid (IID_IMarshal, etc.) are already linked by
// cgoflags_windows_amd64.go.

/*
#cgo LDFLAGS: -lruntimeobject -lshcore -lshell32
*/
import "C"
