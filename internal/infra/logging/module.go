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

var Module = fx.Module("logging",
	fx.Provide(
		func(c *config.Config) (*lumberjack.Logger, *slog.Logger, error) {
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

			w := io.MultiWriter(os.Stdout, rotator)
			logger := slog.New(slog.NewTextHandler(w, &slog.HandlerOptions{
				Level: defaultLogLevel,
			}))

			slog.SetDefault(logger)
			return rotator, logger, nil
		},
	),
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
