//go:build darwin

package wails

import (
	"log/slog"
	"strings"
	"testing"

	"airmedy/internal/app/i18n"
	"airmedy/internal/domain"
)

func TestTrayPillTitleWithoutArtwork(t *testing.T) {
	translations := i18n.NewService(slog.Default())
	track := &domain.TrackDTO{Track: domain.Track{Title: "A song"}}
	if got := trayPillTitle(track, translations); got != "A song • Unknown Artist" {
		t.Fatalf("title without artwork = %q", got)
	}
	track.Title = "  "
	for _, locale := range []string{"de", "en", "es", "fr", "it", "ja", "ko", "pt", "ru", "th", "vi", "zh"} {
		translations.SetLocale(locale)
		if got := trayPillTitle(track, translations); got == "" || got == "tray.unknown_track" || strings.TrimSpace(got) == "" || (locale != "en" && got == "Unknown track") {
			t.Errorf("missing unknown track title for %s: %q", locale, got)
		}
	}
}

func TestTrayPanelMetadataFallbacks(t *testing.T) {
	translations := i18n.NewService(slog.Default())
	translations.SetLocale("en")
	if title, artist := trayTrackMetadata(nil, translations); title != "No track playing" || artist != "" {
		t.Fatalf("no track metadata = %q, %q", title, artist)
	}
	track := &domain.TrackDTO{Track: domain.Track{Title: " "}, Artists: []*domain.Artist{nil, {Name: " "}}}
	if title, artist := trayTrackMetadata(track, translations); title != "Unknown track" || artist != "Unknown Artist" {
		t.Fatalf("missing metadata = %q, %q", title, artist)
	}
}

func TestTrayRepeatCycle(t *testing.T) {
	mode := domain.RepeatModeOff
	for _, want := range []domain.RepeatMode{domain.RepeatModeAll, domain.RepeatModeOne, domain.RepeatModeOff} {
		mode = nextTrayRepeatMode(mode)
		if mode != want {
			t.Fatalf("repeat mode = %q, want %q", mode, want)
		}
	}
}

func TestTrayPillUnknownArtist(t *testing.T) {
	translations := i18n.NewService(slog.Default())
	track := &domain.TrackDTO{Track: domain.Track{Title: "Song"}, Artists: []*domain.Artist{nil, {Name: " "}}}
	for _, locale := range []string{"de", "en", "es", "fr", "it", "ja", "ko", "pt", "ru", "th", "vi", "zh"} {
		translations.SetLocale(locale)
		artist := translations.T("tray.unknown_artist")
		if artist == "tray.unknown_artist" || strings.TrimSpace(artist) == "" || (locale != "en" && artist == "Unknown Artist") {
			t.Errorf("missing unknown artist translation for %s: %q", locale, artist)
		}
		if got := trayPillTitle(track, translations); got != "Song • "+artist {
			t.Errorf("missing artist for %s: %q", locale, got)
		}
	}
}

func TestTrayPillTitleWithArtists(t *testing.T) {
	translations := i18n.NewService(slog.Default())
	track := &domain.TrackDTO{Track: domain.Track{Title: " A song "}, Artists: []*domain.Artist{{Name: " Artist A "}, nil, {Name: "Artist B"}}}
	if got := trayPillTitle(track, translations); got != "A song • Artist A, Artist B" {
		t.Fatalf("title with artists = %q", got)
	}
	track.Title = " "
	if got := trayPillTitle(track, translations); got != "Unknown track • Artist A, Artist B" {
		t.Fatalf("missing title with artists = %q", got)
	}
}

func TestTrayMenuLabelsAreTranslated(t *testing.T) {
	translations := i18n.NewService(slog.Default())
	for _, locale := range []string{"de", "en", "es", "fr", "it", "ja", "ko", "pt", "ru", "th", "vi", "zh"} {
		translations.SetLocale(locale)
		for _, key := range []string{"tray.show_airmedy", "tray.quit", "tray.seek"} {
			if label := translations.T(key); strings.TrimSpace(label) == "" || label == key {
				t.Errorf("missing %s translation for %s: %q", key, locale, label)
			}
		}
	}
}
