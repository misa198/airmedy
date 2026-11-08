package appsettings

import (
	"context"
	"errors"
	"log/slog"
	"testing"

	"airmedy/internal/domain"
)

type backgroundSettingsRepository struct {
	domain.SettingsRepository
	saved *domain.AppSettings
	err   error
}

func (r *backgroundSettingsRepository) Save(_ context.Context, settings *domain.AppSettings) error {
	r.saved = settings
	return r.err
}

func TestSaveSettingsValidatesTrayBackgroundMode(t *testing.T) {
	for _, mode := range []string{"", "solid", "living", "monochrome", "invalid"} {
		t.Run(mode, func(t *testing.T) {
			// Stop at persistence to avoid changing the host's login-item settings.
			stop := errors.New("persistence sentinel")
			repo := &backgroundSettingsRepository{err: stop}
			svc := NewSettingsService(repo, nil, slog.Default())
			settings := &domain.AppSettings{TrayBackgroundMode: mode, PrimaryColor: domain.DefaultPrimaryColor}
			err := svc.SaveSettings(context.Background(), settings)
			if mode == "invalid" {
				if err == nil || repo.saved != nil {
					t.Fatal("invalid mode reached persistence")
				}
				return
			}
			if !errors.Is(err, stop) {
				t.Fatalf("valid mode rejected: %v", err)
			}
			expected := mode
			if expected == "" {
				expected = "solid"
			}
			if repo.saved.TrayBackgroundMode != expected {
				t.Fatalf("saved mode = %q, want %q", repo.saved.TrayBackgroundMode, expected)
			}
		})
	}
}
