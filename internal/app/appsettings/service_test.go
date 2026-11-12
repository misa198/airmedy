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

func TestSaveSettingsAcceptsBlackTrayTheme(t *testing.T) {
	stop := errors.New("persistence sentinel")
	repo := &backgroundSettingsRepository{err: stop}
	svc := NewSettingsService(repo, nil, slog.Default())
	err := svc.SaveSettings(context.Background(), &domain.AppSettings{TrayTheme: "black", PrimaryColor: domain.DefaultPrimaryColor})
	if !errors.Is(err, stop) || repo.saved.TrayTheme != "black" {
		t.Fatalf("black tray theme rejected: %v", err)
	}
}

func TestSaveSettingsValidatesTrayPanelTheme(t *testing.T) {
	for _, theme := range []string{"", "light", "dark", "black", "system", "invalid"} {
		t.Run(theme, func(t *testing.T) {
			stop := errors.New("persistence sentinel")
			repo := &backgroundSettingsRepository{err: stop}
			svc := NewSettingsService(repo, nil, slog.Default())
			err := svc.SaveSettings(context.Background(), &domain.AppSettings{TrayPanelTheme: theme, PrimaryColor: domain.DefaultPrimaryColor})
			if theme == "invalid" {
				if err == nil || repo.saved != nil {
					t.Fatal("invalid theme reached persistence")
				}
				return
			}
			if !errors.Is(err, stop) {
				t.Fatalf("valid theme rejected: %v", err)
			}
			if theme == "" {
				theme = "system"
			}
			if repo.saved.TrayPanelTheme != theme {
				t.Fatalf("saved theme = %q, want %q", repo.saved.TrayPanelTheme, theme)
			}
		})
	}
}

func TestSaveSettingsValidatesTitleBarActions(t *testing.T) {
	for _, name := range []string{"default", "reordered", "empty", "duplicate", "unknown"} {
		t.Run(name, func(t *testing.T) {
			actions := domain.DefaultTitleBarActions()
			switch name {
			case "default":
				actions = nil
			case "reordered":
				actions[0], actions[4] = actions[4], actions[0]
				actions[0].Visible = true
			case "empty":
				actions = []domain.TitleBarAction{}
			case "duplicate":
				actions[0].ID = actions[1].ID
			case "unknown":
				actions[0].ID = "unknown"
			}
			stop := errors.New("persistence sentinel")
			repo := &backgroundSettingsRepository{err: stop}
			svc := NewSettingsService(repo, nil, slog.Default())
			err := svc.SaveSettings(context.Background(), &domain.AppSettings{PrimaryColor: domain.DefaultPrimaryColor, TitleBarActions: actions})
			valid := name == "default" || name == "reordered"
			if valid && !errors.Is(err, stop) {
				t.Fatalf("valid actions rejected: %v", err)
			}
			if !valid && (err == nil || repo.saved != nil) {
				t.Fatal("invalid actions reached persistence")
			}
			if valid && len(repo.saved.TitleBarActions) != 5 {
				t.Fatal("missing defaults")
			}
		})
	}
}

func TestSaveSettingsValidatesSidebarItems(t *testing.T) {
	for _, name := range []string{"default", "reordered", "hidden", "hidden_home", "unknown"} {
		t.Run(name, func(t *testing.T) {
			items := domain.DefaultSidebarItems()
			switch name {
			case "default":
				items = nil
			case "reordered":
				items[0], items[6] = items[6], items[0]
			case "hidden":
				items[1].Visible = false
			case "hidden_home":
				items[0].Visible = false
			case "unknown":
				items[0].ID = "unknown"
			}
			stop := errors.New("persistence sentinel")
			repo := &backgroundSettingsRepository{err: stop}
			svc := NewSettingsService(repo, nil, slog.Default())
			err := svc.SaveSettings(context.Background(), &domain.AppSettings{PrimaryColor: domain.DefaultPrimaryColor, SidebarItems: items})
			valid := name == "default" || name == "reordered" || name == "hidden"
			if valid && !errors.Is(err, stop) {
				t.Fatalf("valid sidebar items rejected: %v", err)
			}
			if !valid && (err == nil || repo.saved != nil) {
				t.Fatal("invalid sidebar items reached persistence")
			}
			if valid && len(repo.saved.SidebarItems) != 7 {
				t.Fatal("missing defaults")
			}
		})
	}
}

func (r *backgroundSettingsRepository) Load(context.Context) (*domain.AppSettings, error) {
	return nil, r.err
}

func TestGetSettingsDefaultsHideTitleBarNavigation(t *testing.T) {
	repo := &backgroundSettingsRepository{err: errors.New("load failed")}
	svc := NewSettingsService(repo, nil, slog.Default())
	settings, err := svc.GetSettings(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if settings.TitleBarShowNavigation {
		t.Fatal("fallback defaults must hide title bar navigation")
	}
}
