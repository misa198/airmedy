//go:build windows

package wails

// CGO linker flags for thumbbar_windows.cpp (Windows Taskbar Thumbnail Toolbar).
//
//   -lcomctl32  SetWindowSubclass / DefSubclassProc / RemoveWindowSubclass
//   -lole32     CoInitializeEx / CoCreateInstance / CoUninitialize
//   -luuid      CLSID_TaskbarList / IID_ITaskbarList3 GUIDs

/*
#cgo LDFLAGS: -lcomctl32 -lgdi32 -lole32 -luuid
*/
import "C"
