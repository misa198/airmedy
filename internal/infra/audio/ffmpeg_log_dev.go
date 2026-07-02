//go:build !production

package audio

// Dev build: no-op, leaves libav's default log level (AV_LOG_INFO) so its
// native stderr output stays visible. See ffmpeg_log_production.go.
