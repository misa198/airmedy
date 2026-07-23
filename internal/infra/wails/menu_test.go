package wails

import (
	"io"
	"log/slog"
	"runtime"
	"testing"

	"airmedy/internal/app/i18n"
	"github.com/wailsapp/wails/v3/pkg/application"
)

func TestBuildAppMenuStructure(t *testing.T) {
	i18nService := i18n.NewService(slog.New(slog.NewTextHandler(io.Discard, nil)))
	wailsApp := application.New(application.Options{Name: "Airmedy"})
	menu := BuildAppMenu(wailsApp, i18nService, nil)

	wantTopLevel := []string{"Edit", "Playback", "Window", "Help"}
	if runtime.GOOS == "darwin" {
		wantTopLevel = append([]string{"Airmedy"}, wantTopLevel...)
	}
	for i, want := range wantTopLevel {
		item := menu.ItemAt(i)
		if item == nil || item.Label() != want {
			t.Fatalf("top-level item %d = %v, want %q", i, item, want)
		}
	}
	if item := menu.ItemAt(len(wantTopLevel)); item != nil {
		t.Fatalf("unexpected top-level item %q", item.Label())
	}
	for _, removed := range []string{"File", "View"} {
		if item := menu.FindByLabel(removed); item != nil {
			t.Errorf("removed menu %q is still present", removed)
		}
	}
	for _, want := range []string{"GitHub", "Sponsor"} {
		if item := menu.FindByLabel(want); item == nil {
			t.Errorf("Help menu item %q is missing", want)
		}
	}
}
