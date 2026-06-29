//go:build windows

package audio

// Linker flags for the Windows SMTC backend (smtc_windows.cpp). Kept in a
// windows-wide file (not the amd64-only cgoflags) so arm64 builds also link
// these WinRT/shell libraries.
//
//   -lruntimeobject : RoInitialize / RoGetActivationFactory / RoActivateInstance
//                     and the WinRT HSTRING helpers (WindowsCreateString, ...).
//   -lshcore        : CreateRandomAccessStreamOnFile (artwork file -> WinRT stream).
//   -lshell32       : SetCurrentProcessExplicitAppUserModelID, SHGetFolderPathW,
//                     CLSID_ShellLink / IShellLink (Start Menu shortcut).
//   -lpropsys       : IID_IPropertyStore (AppUserModelID property on the shortcut).
//
// ole32 / oleaut32 / uuid (IID_IMarshal, CLSID_ShellLink, IID_IPersistFile, etc.)
// are already linked by cgoflags_windows_amd64.go.

/*
#cgo LDFLAGS: -lruntimeobject -lshcore -lshell32 -lpropsys
*/
import "C"
