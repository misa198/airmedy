package main

import (
	"context"
	"embed"
	"log/slog"
	"net/url"
	"os"
	"runtime"
	"sync"
	"time"

	"airmedy/internal/app"
	"airmedy/internal/app/config"
	"airmedy/internal/app/i18n"
	"airmedy/internal/domain"
	"airmedy/internal/infra/wails"
	"runtime/debug"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
	"github.com/wailsapp/wails/v3/pkg/icons"
	"go.uber.org/fx"
)

//go:embed all:frontend/dist
var assets embed.FS

//go:embed assets/mac-tray-icon.png
var macTrayIcon []byte

//go:embed assets/linux-tray-icon.png
var linuxTrayIcon []byte

//go:embed assets/windows-tray-icon.png
var windowsTrayIcon []byte

func init() {
	application.RegisterEvent[string]("time")
	application.RegisterEvent[string]("language:changed")
}

func main() {
	debug.SetGCPercent(50)

	if err := registerProtocol(); err != nil {
		slog.Warn("failed to register deep link protocol", "error", err)
	}

	var greetService *wails.GreetService
	var libraryService *wails.LibraryService
	var playerService *wails.PlayerService
	var searchService *wails.SearchService
	var playlistService *wails.PlaylistService
	var lyricsService *wails.LyricsService
	var eqService *wails.EQService
	var windowService *wails.WindowService
	var i18nService *i18n.Service
	var settingsService *wails.SettingsService
	var updaterService *wails.UpdaterService
	var artworkCache domain.ArtworkCache
	var (
		lastfmService *wails.LastFmService
		wailsApp      *application.App
	)

	slog.Info("Starting Airmedy", "version", config.Version)

	fxApp := fx.New(
		app.Module,
		fx.Populate(&greetService, &libraryService, &playerService, &searchService, &playlistService, &lyricsService, &eqService, &windowService, &i18nService, &settingsService, &lastfmService, &updaterService, &artworkCache),
		fx.NopLogger, // Keep logs clean for now
	)

	startCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := fxApp.Start(startCtx); err != nil {
		slog.Error("failed to start services", "error", err)
		os.Exit(1)
	}

	var stopOnce sync.Once
	stopFX := func() {
		stopOnce.Do(func() {
			stopCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			if err := fxApp.Stop(stopCtx); err != nil {
				slog.Error("error stopping services", "error", err)
			}
		})
	}

	var mainWindow *application.WebviewWindow

	wailsApp = application.New(application.Options{
		Name:        "airmedy",
		Description: "A modern music player",
		SingleInstance: &application.SingleInstanceOptions{
			UniqueID: "me.misa198.airmedy",
			OnSecondInstanceLaunch: func(data application.SecondInstanceData) {
				if mainWindow != nil {
					mainWindow.Show()
					mainWindow.Focus()
				}
			},
		},
		Services: []application.Service{
			application.NewService(greetService),
			application.NewService(libraryService),
			application.NewService(playerService),
			application.NewService(searchService),
			application.NewService(playlistService),
			application.NewService(lyricsService),
			application.NewService(eqService),
			application.NewService(lastfmService),
			application.NewService(windowService),
			application.NewService(settingsService),
			application.NewService(updaterService),
		},
		Assets: application.AssetOptions{
			Handler: wails.NewAssetHandler(assets, artworkCache),
		},
		Mac: application.MacOptions{
			ApplicationShouldTerminateAfterLastWindowClosed: false,
		},
	})

	// Initialize i18n
	settings, _ := settingsService.GetSettings(context.Background())
	i18nService.SetLocale(settings.Language)

	// Create application menu
	menu := buildAppMenu(wailsApp, i18nService, playerService)
	wailsApp.Menu.SetApplicationMenu(menu)

	mainWindow = wailsApp.Window.NewWithOptions(application.WebviewWindowOptions{
		Title:              "Airmedy",
		Width:              1280,
		Height:             800,
		MinWidth:           1200,
		MinHeight:          768,
		UseApplicationMenu: runtime.GOOS == "darwin",
		Mac: application.MacWindow{
			InvisibleTitleBarHeight: 50,
			Backdrop:                application.MacBackdropTranslucent,
			TitleBar:                application.MacTitleBarHiddenInset,
		},
		BackgroundColour: application.NewRGB(27, 38, 54),
		URL:              "/",
	})

	mainWindow.RegisterHook(events.Common.WindowClosing, func(e *application.WindowEvent) {
		mainWindow.Hide()
		e.Cancel()
	})
	windowService.SetMainWindow(mainWindow)
	mainWindow.Show()
	mainWindow.Focus()

	windowService.SetMiniWindowFactory(func() *application.WebviewWindow {
		w := wailsApp.Window.NewWithOptions(application.WebviewWindowOptions{
			Title:               "Mini Player",
			Width:               300,
			Height:              300,
			MinWidth:            280,
			MinHeight:           280,
			MaxWidth:            500,
			MaxHeight:           500,
			Hidden:              true,
			AlwaysOnTop:         false,
			DisableResize:       false,
			MinimiseButtonState: application.ButtonHidden,
			MaximiseButtonState: application.ButtonHidden,
			CloseButtonState:    application.ButtonHidden,
			Mac: application.MacWindow{
				InvisibleTitleBarHeight: 28,
				Backdrop:                application.MacBackdropTranslucent,
				TitleBar:                application.MacTitleBarHiddenInset,
				CollectionBehavior:      application.MacWindowCollectionBehaviorTransient,
			},
			BackgroundColour: application.NewRGB(27, 38, 54),
			URL:              "/?mode=mini#/mini-player",
		})
		w.RegisterHook(events.Common.WindowClosing, func(e *application.WindowEvent) {
			windowService.OnMiniPlayerClosed()
			// No e.Cancel() — Wails destroys the window, freeing its memory
		})
		return w
	})

	// Handle deep links (e.g. airmedy://auth?token=...)
	wailsApp.Event.OnApplicationEvent(events.Common.ApplicationLaunchedWithUrl, func(e *application.ApplicationEvent) {
		urlStr := e.Context().URL()
		if urlStr == "" {
			return
		}
		if u, err := url.Parse(urlStr); err == nil {
			if u.Scheme == "airmedy" && u.Host == "auth" {
				token := u.Query().Get("token")
				if token != "" {
					go func() {
						if err := lastfmService.GetService().CompleteAuth(context.Background(), token); err != nil {
							slog.Error("failed to complete Last.fm auth", "error", err)
						} else {
							slog.Info("Last.fm auth completed successfully")
							if wailsApp != nil {
								wailsApp.Event.Emit("lastfm:connected")
							}
						}
					}()
				}
			}
		}
	})

	// Cmd+Q fires ApplicationWillTerminate, bypassing WindowClosing on macOS.
	wailsApp.Event.OnApplicationEvent(events.Mac.ApplicationWillTerminate, func(_ *application.ApplicationEvent) {
		stopFX()
	})

	var trayManager *wails.TrayManager
	settings, err := settingsService.GetSettings(context.Background())
	if err == nil && settings.ShowTrayIcon {
		systemTray := wailsApp.SystemTray.New()
		switch runtime.GOOS {
		case "darwin":
			systemTray.SetTemplateIcon(icons.SystrayMacTemplate)
			systemTray.SetIcon(macTrayIcon)
		case "linux":
			systemTray.SetIcon(linuxTrayIcon)
		case "windows":
			systemTray.SetIcon(windowsTrayIcon)
		}
		systemTray.SetTooltip("Airmedy")

		trayManager = wails.NewTrayManager(wailsApp, playerService.GetService(), libraryService, i18nService)
		trayManager.Setup(systemTray, mainWindow)
	}

	// Listen for language changes
	wailsApp.Event.On("language:changed", func(event *application.CustomEvent) {
		if lang, ok := event.Data.(string); ok {
			i18nService.SetLocale(lang)
			application.InvokeSync(func() {
				newMenu := buildAppMenu(wailsApp, i18nService, playerService)
				wailsApp.Menu.SetApplicationMenu(newMenu)
				if trayManager != nil {
					trayManager.UpdateLanguage()
				}
			})
		}
	})

	go func() {
		for {
			now := time.Now().Format(time.RFC1123)
			wailsApp.Event.Emit("time", now)
			time.Sleep(time.Second)
		}
	}()

	if err := wailsApp.Run(); err != nil {
		slog.Error("application error", "error", err)
		os.Exit(1)
	}

	stopFX()
}

func buildAppMenu(wailsApp *application.App, i18nService *i18n.Service, playerService *wails.PlayerService) *application.Menu {
	menu := application.NewMenu()
	if runtime.GOOS == "darwin" {
		appMenu := menu.AddSubmenu(i18nService.T("menu.airmedy"))
		appMenu.AddRole(application.About)
		appMenu.AddSeparator()
		appMenu.Add(i18nService.T("menu.settings")).
			SetAccelerator("Cmd+,").
			OnClick(func(ctx *application.Context) {
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

		menu.AddRole(application.FileMenu)
	} else {
		fileMenu := menu.AddSubmenu(i18nService.T("menu.file"))
		fileMenu.Add(i18nService.T("menu.settings")).
			SetAccelerator("Ctrl+,").
			OnClick(func(ctx *application.Context) {
				wailsApp.Event.Emit("open-settings")
			})
		fileMenu.AddSeparator()
		fileMenu.AddRole(application.Quit)
	}

	menu.AddRole(application.EditMenu)

	// Playback menu
	playbackMenu := menu.AddSubmenu(i18nService.T("menu.playback"))
	var ctrl, opt string
	if runtime.GOOS == "darwin" {
		ctrl = "Cmd"
		opt = "Option"
	} else {
		ctrl = "Ctrl"
		opt = "Alt"
	}

	playbackMenu.Add(i18nService.T("menu.play_pause")).
		SetAccelerator("Space").
		OnClick(func(ctx *application.Context) {
			_ = playerService.TogglePause()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.next_track")).
		SetAccelerator(ctrl + "+Right").
		OnClick(func(ctx *application.Context) {
			_ = playerService.Next()
		})
	playbackMenu.Add(i18nService.T("menu.previous_track")).
		SetAccelerator(ctrl + "+Left").
		OnClick(func(ctx *application.Context) {
			_ = playerService.Previous()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.fast_forward")).
		SetAccelerator(opt + "+" + ctrl + "+Right").
		OnClick(func(ctx *application.Context) {
			_ = playerService.FastForward()
		})
	playbackMenu.Add(i18nService.T("menu.rewind")).
		SetAccelerator(opt + "+" + ctrl + "+Left").
		OnClick(func(ctx *application.Context) {
			_ = playerService.Rewind()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.increase_volume")).
		SetAccelerator(ctrl + "+Up").
		OnClick(func(ctx *application.Context) {
			_ = playerService.IncreaseVolume()
		})
	playbackMenu.Add(i18nService.T("menu.decrease_volume")).
		SetAccelerator(ctrl + "+Down").
		OnClick(func(ctx *application.Context) {
			_ = playerService.DecreaseVolume()
		})
	playbackMenu.Add(i18nService.T("menu.mute")).
		SetAccelerator(opt + "+" + ctrl + "+Down").
		OnClick(func(ctx *application.Context) {
			_ = playerService.ToggleMute()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add(i18nService.T("menu.shuffle")).
		SetAccelerator(ctrl + "+S").
		OnClick(func(ctx *application.Context) {
			status := playerService.GetStatus()
			_ = playerService.SetShuffle(!status.Shuffle)
		})
	playbackMenu.Add(i18nService.T("menu.repeat")).
		SetAccelerator(ctrl + "+R").
		OnClick(func(ctx *application.Context) {
			wailsApp.Event.Emit("player:cycle-repeat")
		})

	// View menu
	viewMenu := menu.AddSubmenu(i18nService.T("menu.view"))
	viewMenu.Add(i18nService.T("menu.search")).
		SetAccelerator(ctrl + "+F").
		OnClick(func(ctx *application.Context) {
			wailsApp.Event.Emit("open-search")
		})
	viewMenu.AddSeparator()
	viewMenu.AddRole(application.Reload)
	viewMenu.AddRole(application.ForceReload)
	viewMenu.AddRole(application.ToggleFullscreen)
	viewMenu.AddRole(application.OpenDevTools)

	menu.AddRole(application.WindowMenu)
	menu.AddRole(application.HelpMenu)

	return menu
}
