package i18n

import (
	"io"
	"log/slog"
	"testing"
)

func TestMenuHelpTranslationsExistForAllLocales(t *testing.T) {
	t.Parallel()

	service := NewService(slog.New(slog.NewTextHandler(io.Discard, nil)))
	for _, locale := range []string{"de", "en", "es", "fr", "it", "ja", "ko", "pt", "ru", "th", "vi", "zh"} {
		t.Run(locale, func(t *testing.T) {
			for _, key := range []string{"help", "github", "sponsor"} {
				if value := service.lookup(locale, []string{"menu", key}); value == "" {
					t.Errorf("translation menu.%s is missing", key)
				}
			}
		})
	}
}
