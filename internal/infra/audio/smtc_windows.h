/*
 * smtc_windows.h — C-ABI bridge for Windows System Media Transport Controls.
 *
 * Implemented in smtc_windows.cpp (compiled by CXX/g++ via cgo). The Go layer
 * (player_nowplaying_windows.go) calls the Smtc* functions; the native layer
 * calls back into the goWinNowPlaying* functions exported from Go.
 *
 * SMTC is process-global (one set of transport controls per process), so there
 * is no per-player handle — the native layer owns a single guarded state struct.
 */
#ifndef SMTC_WINDOWS_H
#define SMTC_WINDOWS_H

#ifdef __cplusplus
extern "C" {
#endif

/* Lifecycle. Both are idempotent and safe to call when never started. */
void SmtcStart(void);
void SmtcStop(void);

/* Push full Now Playing metadata + artwork + timeline. Strings are UTF-8;
 * the native layer widens them to UTF-16 internally. artworkPath may be empty. */
void SmtcUpdate(const char* title, const char* artist, const char* album,
                double duration, double position, const char* artworkPath);

/* Update only the timeline position (seconds). */
void SmtcUpdatePosition(double position);

/* Set the playing/paused glyph. playing != 0 => Playing, else Paused. */
void SmtcSetPlaybackStatus(int playing);

/* Clear the Now Playing card (status Stopped). */
void SmtcClear(void);

/* Implemented in Go (player_nowplaying_windows.go) — invoked from the SMTC
 * STA thread when the OS raises a media button / position-change request. */
extern void goWinNowPlayingPlay(void);
extern void goWinNowPlayingPause(void);
extern void goWinNowPlayingNext(void);
extern void goWinNowPlayingPrevious(void);
extern void goWinNowPlayingSeek(double position);

/* Invoked from the STA thread when Windows activates the SMTC window (e.g.,
 * user clicks "Now Playing" in the media flyout). Go decides which app window
 * to bring to front (main or mini player). */
extern void goWinNowPlayingActivate(void);

#ifdef __cplusplus
}
#endif

#endif /* SMTC_WINDOWS_H */
