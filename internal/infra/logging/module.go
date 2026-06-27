package logging

import (
	"airmedy/internal/app/config"
	"context"
	"io"
	"log/slog"
	"os"
	"time"

	"github.com/natefinch/lumberjack"
	"go.uber.org/fx"
)

// NewFileLogger builds the file-backed slog logger and installs it as the
// process default. It is called from main() BEFORE the fx graph is built so
// that bootstrap logs and any fx startup failure are captured to disk — on
// Windows GUI builds there is no stderr, so an early os.Exit would otherwise
// leave no trace at all (notably when an arch-specific provider fails to init).
func NewFileLogger(c *config.Config) (*lumberjack.Logger, *slog.Logger, error) {
	logDir := c.LogDir()
	if err := os.MkdirAll(logDir, 0755); err != nil {
		return nil, nil, err
	}

	rotator := &lumberjack.Logger{
		Filename:   c.LogPath(),
		MaxSize:    10, // Megabytes
		MaxBackups: 7,
		MaxAge:     7,    // Days
		Compress:   true,
		LocalTime:  true,
	}

	// In production (Windows GUI build has no console) os.Stdout is an invalid
	// handle; io.MultiWriter aborts on its write error and never reaches the
	// rotator, so no log file is created. Write only to the file in production;
	// add stdout only in dev.
	var w io.Writer = rotator
	if !config.IsProduction {
		w = io.MultiWriter(os.Stdout, rotator)
	}
	logger := slog.New(slog.NewTextHandler(w, &slog.HandlerOptions{
		Level: defaultLogLevel,
	}))

	slog.SetDefault(logger)
	return rotator, logger, nil
}

// Module wires the already-constructed logger (supplied from main) into the fx
// lifecycle for log rotation. The logger itself is built earlier via
// NewFileLogger so it exists before fx.Start can fail.
var Module = fx.Module("logging",
	fx.Invoke(func(lc fx.Lifecycle, rotator *lumberjack.Logger, logger *slog.Logger) {
		workerCtx, cancel := context.WithCancel(context.Background())
		lc.Append(fx.Hook{
			OnStart: func(ctx context.Context) error {
				go func() {
					for {
						now := time.Now()
						next := now.Add(24 * time.Hour).Truncate(24 * time.Hour)
						timer := time.NewTimer(next.Sub(now))

						select {
						case <-timer.C:
							if err := rotator.Rotate(); err != nil {
								logger.Error("Failed to rotate logs", "error", err)
							}
						case <-workerCtx.Done():
							timer.Stop()
							return
						}
					}
				}()
				return nil
			},
			OnStop: func(ctx context.Context) error {
				cancel()
				return rotator.Close()
			},
		})
	}),
)
