//go:build !darwin && !linux

package sysinfo

import "log/slog"

// RaiseFileDescriptorLimit is a no-op outside darwin/linux — Windows has no
// RLIMIT_NOFILE concept (per-process handle limits are managed differently
// and are not the fd-exhaustion failure mode this addresses).
func RaiseFileDescriptorLimit(_ *slog.Logger) {}
