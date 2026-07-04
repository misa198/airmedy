//go:build darwin || linux

// Package sysinfo raises OS-level resource limits the app needs at startup.
package sysinfo

import (
	"log/slog"

	"golang.org/x/sys/unix"
)

// targetNoFile is the soft fd limit we ask for. GUI-launched macOS apps
// default to a soft RLIMIT_NOFILE of 256 (unlike a Terminal shell, which
// sources a profile that usually raises it). That is tight once SFBAudioEngine
// decoders, bleve index segments, the SQLite handle and OS graphics libraries
// are all open at once, so raise it for headroom. (The library no longer uses a
// real-time file watcher — that held one fd per watched file on kqueue and was
// the original exhaustion source; it was replaced by periodic rescans.)
const targetNoFile = 8192

// RaiseFileDescriptorLimit raises the process's soft RLIMIT_NOFILE toward
// targetNoFile (capped at the hard limit). Best-effort: logs and continues on
// failure rather than blocking startup.
func RaiseFileDescriptorLimit(logger *slog.Logger) {
	var rlimit unix.Rlimit
	if err := unix.Getrlimit(unix.RLIMIT_NOFILE, &rlimit); err != nil {
		logger.Warn("sysinfo: failed to read RLIMIT_NOFILE", "error", err)
		return
	}

	want := uint64(targetNoFile)
	if rlimit.Max != unix.RLIM_INFINITY && want > rlimit.Max {
		want = rlimit.Max
	}
	if rlimit.Cur >= want {
		return
	}

	prev := rlimit.Cur
	rlimit.Cur = want
	if err := unix.Setrlimit(unix.RLIMIT_NOFILE, &rlimit); err != nil {
		logger.Warn("sysinfo: failed to raise RLIMIT_NOFILE", "from", prev, "wanted", want, "error", err)
		return
	}
	logger.Info("sysinfo: raised RLIMIT_NOFILE", "from", prev, "to", want)
}
