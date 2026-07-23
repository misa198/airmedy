package wails

import (
	"log/slog"
	"runtime"

	"airmedy/internal/app/i18n"
	"airmedy/internal/domain"
	"github.com/wailsapp/wails/v3/pkg/application"
)

// PlayerMenuController contains the playback actions exposed by the application menu.
type PlayerMenuController interface {
	TogglePause() error
	Next() error
	Previous() error
	FastForward() error
	Rewind() error
	IncreaseVolume() error
	DecreaseVolume() error
	ToggleMute() error
	SetShuffle(bool) error
	GetStatus() domain.PlayerStatus
}

// BuildAppMenu creates the native application menu and wires its actions to Wails services.
func BuildAppMenu(wailsApp *application.App, i18nService *i18n.Service, playerService PlayerMenuController) *application.Menu {
	menu := application.NewMenu()
	if runtime.GOOS == "darwin" {
		appMenu := menu.AddSubmenu(i18nService.T("menu.airmedy"))
		appMenu.AddRole(application.About)
		appMenu.AddSeparator()
		appMenu.Add(i18nService.T("menu.settings")).SetAccelerator("Cmd+,").OnClick(func(_ *application.Context) {
			wailsApp.Event.Emit("open-settings")
		})
		appMenu.AddSeparator()
		appMenu.AddRole(application.ServicesMenu)
		appMenu.AddSeparator()
		appMenu.AddRole(application.Hide)
		appMenu.AddRole(application.HideOthers)
		appMenu.AddRole(application.ShowAll)
		appMenu.AddSeparator()
		appMenu.AddRole(application.Quit)
	}

	menu.AddRole(application.EditMenu)
	playbackMenu := menu.AddSubmenu(i18nService.T("menu.playback"))
	ctrl, opt := "Ctrl", "Alt"
	if runtime.GOOS == "darwin" {
		ctrl, opt = "Cmd", "Option"
	}
	playbackMenu.Add(i18nService.T("menu.play_pause")).SetAccelerator("Space").OnClick(func(_ *application.Context) { _ = playerService.TogglePause() })
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.next_track")).SetAccelerator(ctrl + "+Right").OnClick(func(_ *application.Context) { _ = playerService.Next() })
	playbackMenu.Add(i18nService.T("menu.previous_track")).SetAccelerator(ctrl + "+Left").OnClick(func(_ *application.Context) { _ = playerService.Previous() })
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.fast_forward")).SetAccelerator(opt + "+" + ctrl + "+Right").OnClick(func(_ *application.Context) { _ = playerService.FastForward() })
	playbackMenu.Add(i18nService.T("menu.rewind")).SetAccelerator(opt + "+" + ctrl + "+Left").OnClick(func(_ *application.Context) { _ = playerService.Rewind() })
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.increase_volume")).SetAccelerator(ctrl + "+Up").OnClick(func(_ *application.Context) { _ = playerService.IncreaseVolume() })
	playbackMenu.Add(i18nService.T("menu.decrease_volume")).SetAccelerator(ctrl + "+Down").OnClick(func(_ *application.Context) { _ = playerService.DecreaseVolume() })
	playbackMenu.Add(i18nService.T("menu.mute")).SetAccelerator(opt + "+" + ctrl + "+Down").OnClick(func(_ *application.Context) { _ = playerService.ToggleMute() })
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.shuffle")).SetAccelerator(ctrl + "+S").OnClick(func(_ *application.Context) {
		status := playerService.GetStatus()
		_ = playerService.SetShuffle(!status.Shuffle)
	})
	playbackMenu.Add(i18nService.T("menu.repeat")).SetAccelerator(ctrl + "+R").OnClick(func(_ *application.Context) {
		wailsApp.Event.Emit("player:cycle-repeat")
	})

	menu.AddRole(application.WindowMenu)
	helpMenu := menu.AddSubmenu(i18nService.T("menu.help"))
	helpMenu.Add(i18nService.T("menu.github")).OnClick(func(_ *application.Context) {
		if err := wailsApp.Browser.OpenURL("https://github.com/misa198/airmedy"); err != nil {
			slog.Error("failed to open GitHub repository", "error", err)
		}
	})
	helpMenu.Add(i18nService.T("menu.sponsor")).OnClick(func(_ *application.Context) {
		if err := wailsApp.Browser.OpenURL("https://github.com/sponsors/misa198"); err != nil {
			slog.Error("failed to open GitHub Sponsors", "error", err)
		}
	})
	return menu
}
