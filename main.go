package main

import (
	"context"
	"embed"
	_ "embed"
	"log"
	"net/url"
	"runtime"
	"sync"
	"time"

	"airmedy/internal/app"
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

//go:embed assets/tray-icon.png
var trayIcon []byte

func init() {
	application.RegisterEvent[string]("time")
}

func main() {
	debug.SetGCPercent(50)

	var greetService *wails.GreetService
	var libraryService *wails.LibraryService
	var playerService *wails.PlayerService
	var searchService *wails.SearchService
	var playlistService *wails.PlaylistService
	var lyricsService *wails.LyricsService
	var eqService *wails.EQService
	var windowService *wails.WindowService
	var settingsService *wails.SettingsService
	var lastfmService *wails.LastFmService
	var artworkCache domain.ArtworkCache

	fxApp := fx.New(
		app.Module,
		fx.Populate(&greetService, &libraryService, &playerService, &searchService, &playlistService, &lyricsService, &eqService, &windowService, &settingsService, &lastfmService, &artworkCache),
		fx.NopLogger, // Keep logs clean for now
	)

	startCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := fxApp.Start(startCtx); err != nil {
		log.Fatal(err)
	}

	var stopOnce sync.Once
	stopFX := func() {
		stopOnce.Do(func() {
			stopCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
			defer cancel()
			if err := fxApp.Stop(stopCtx); err != nil {
				log.Printf("error stopping services: %v", err)
			}
		})
	}

	wailsApp := application.New(application.Options{
		Name:        "airmedy",
		Description: "A modern music player",
		// Protocols: []application.Protocol{
		// 	{
		// 		Scheme: "airmedy",
		// 	},
		// },
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
		},
		Assets: application.AssetOptions{
			Handler: wails.NewAssetHandler(assets, artworkCache),
		},
		Mac: application.MacOptions{
			ApplicationShouldTerminateAfterLastWindowClosed: false,
		},
	})

	// Create application menu
	menu := application.NewMenu()
	if runtime.GOOS == "darwin" {
		appMenu := menu.AddSubmenu("airmedy")
		appMenu.AddRole(application.About)
		appMenu.AddSeparator()
		appMenu.Add("Settings...").
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
		fileMenu := menu.AddSubmenu("File")
		fileMenu.Add("Settings...").
			SetAccelerator("Ctrl+,").
			OnClick(func(ctx *application.Context) {
				wailsApp.Event.Emit("open-settings")
			})
		fileMenu.AddSeparator()
		fileMenu.AddRole(application.Quit)
	}

	menu.AddRole(application.EditMenu)

	// Playback menu
	playbackMenu := menu.AddSubmenu("Playback")
	var ctrl, opt string
	if runtime.GOOS == "darwin" {
		ctrl = "Cmd"
		opt = "Option"
	} else {
		ctrl = "Ctrl"
		opt = "Alt"
	}

	playbackMenu.Add("Play/Pause").
		SetAccelerator("Space").
		OnClick(func(ctx *application.Context) {
			_ = playerService.TogglePause()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add("Next Track").
		SetAccelerator(ctrl + "+Right").
		OnClick(func(ctx *application.Context) {
			_ = playerService.Next()
		})
	playbackMenu.Add("Previous Track").
		SetAccelerator(ctrl + "+Left").
		OnClick(func(ctx *application.Context) {
			_ = playerService.Previous()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add("Fast Forward").
		SetAccelerator(opt + "+" + ctrl + "+Right").
		OnClick(func(ctx *application.Context) {
			_ = playerService.FastForward()
		})
	playbackMenu.Add("Rewind").
		SetAccelerator(opt + "+" + ctrl + "+Left").
		OnClick(func(ctx *application.Context) {
			_ = playerService.Rewind()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add("Increase Volume").
		SetAccelerator(ctrl + "+Up").
		OnClick(func(ctx *application.Context) {
			_ = playerService.IncreaseVolume()
		})
	playbackMenu.Add("Decrease Volume").
		SetAccelerator(ctrl + "+Down").
		OnClick(func(ctx *application.Context) {
			_ = playerService.DecreaseVolume()
		})
	playbackMenu.Add("Mute").
		SetAccelerator(opt + "+" + ctrl + "+Down").
		OnClick(func(ctx *application.Context) {
			_ = playerService.ToggleMute()
		})
	playbackMenu.AddSeparator()
	playbackMenu.Add("Shuffle").
		SetAccelerator(ctrl + "+S").
		OnClick(func(ctx *application.Context) {
			status := playerService.GetStatus()
			_ = playerService.SetShuffle(!status.Shuffle)
		})
	playbackMenu.Add("Repeat").
		SetAccelerator(ctrl + "+R").
		OnClick(func(ctx *application.Context) {
			wailsApp.Event.Emit("player:cycle-repeat")
		})

	// View menu
	viewMenu := menu.AddSubmenu("View")
	viewMenu.Add("Search").
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

	wailsApp.Menu.SetApplicationMenu(menu)

	mainWindow := wailsApp.Window.NewWithOptions(application.WebviewWindowOptions{
		Title:     "Airmedy",
		Width:     1280,
		Height:    800,
		MinWidth:  1060,
		MinHeight: 768,
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

	miniPlayerWindow := wailsApp.Window.NewWithOptions(application.WebviewWindowOptions{
		Title:               "Mini Player",
		Width:               300,
		Height:              300,
		MinWidth:            280,
		MinHeight:           280,
		MaxWidth:            500,
		MaxHeight:           500,
		Hidden:              true,
		AlwaysOnTop:         true,
		DisableResize:       false,
		MinimiseButtonState: application.ButtonHidden,
		MaximiseButtonState: application.ButtonHidden,
		CloseButtonState:    application.ButtonHidden,
		Mac: application.MacWindow{
			InvisibleTitleBarHeight: 28,
			Backdrop:                application.MacBackdropTranslucent,
			TitleBar:                application.MacTitleBarHiddenInset,
		},
		BackgroundColour: application.NewRGB(27, 38, 54),
		URL:              "/#/mini-player",
	})
	miniPlayerWindow.RegisterHook(events.Common.WindowClosing, func(e *application.WindowEvent) {
		windowService.CloseMiniPlayer()
		e.Cancel()
	})
	windowService.SetMiniWindow(miniPlayerWindow)

	// Handle deep links (e.g. airmedy://auth?token=...)
	wailsApp.Event.OnApplicationEvent(events.Common.ApplicationLaunchedWithUrl, func(e *application.ApplicationEvent) {
		log.Printf("Application opened with URL: %s", e.Context().URL())
		urlStr := e.Context().URL()
		if urlStr == "" {
			return
		}
		log.Printf("Application opened with URL: %s", urlStr)
		if u, err := url.Parse(urlStr); err == nil {
			if u.Scheme == "airmedy" && u.Host == "auth" {
				token := u.Query().Get("token")
				if token != "" {
					go func() {
						if err := lastfmService.GetService().CompleteAuth(context.Background(), token); err != nil {
							log.Printf("Failed to complete Last.fm auth: %v", err)
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

	systemTray := wailsApp.SystemTray.New()
	systemTray.SetTemplateIcon(icons.SystrayMacTemplate)
	systemTray.SetIcon(trayIcon)
	systemTray.SetTooltip("Airmedy")

	trayManager := wails.NewTrayManager(wailsApp, playerService.GetService(), libraryService)
	trayManager.Setup(systemTray, mainWindow)

	go func() {
		for {
			now := time.Now().Format(time.RFC1123)
			wailsApp.Event.Emit("time", now)
			time.Sleep(time.Second)
		}
	}()

	err := wailsApp.Run()
	if err != nil {
		log.Fatal(err)
	}

	stopFX()
}
