/*
 * thumbbar_windows.h — C-ABI bridge for Windows Taskbar Thumbnail Toolbar.
 *
 * Implemented in thumbbar_windows.cpp (compiled by CXX/g++ via cgo). The Go
 * layer (thumbbar_manager_windows.go) calls ThumbBar*; the native layer calls
 * back into the goThumbBar* functions exported from Go.
 *
 * Displays Prev / Play-Pause / Next buttons in the taskbar thumbnail popup.
 */
#ifndef THUMBBAR_WINDOWS_H
#define THUMBBAR_WINDOWS_H

#ifdef __cplusplus
extern "C" {
#endif

/* Initialize the thumbnail toolbar for the given HWND.
 * Must be called on the Win32 message thread (via application.InvokeAsync).
 * playing != 0 → initial icon shows Pause; else Play. */
void ThumbBarInit(void* hwnd, int playing);

/* Thread-safe: posts a message to the window thread.
 * playing != 0 → show Pause icon; else Play icon. */
void ThumbBarSetPlaying(int playing);

/* Remove the subclass, release COM objects, destroy icons. */
void ThumbBarStop(void);

/* Implemented in Go (thumbbar_manager_windows.go). Invoked from the
 * subclass WndProc on WM_COMMAND / THBN_CLICKED on the main message thread. */
extern void goThumbBarPrev(void);
extern void goThumbBarPlayPause(void);
extern void goThumbBarNext(void);

#ifdef __cplusplus
}
#endif

#endif /* THUMBBAR_WINDOWS_H */
