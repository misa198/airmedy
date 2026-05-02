package main

import (
	"context"
	"embed"
	_ "embed"
	"log/slog"
	"net/url"
	"os"
	"runtime"
	"strings"
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

//go:embed assets/mac-tray-icon.png
var macTrayIcon []byte

//go:embed assets/linux-tray-icon.png
var linuxTrayIcon []byte

//go:embed assets/windows-tray-icon.png
var windowsTrayIcon []byte

func init() {
	application.RegisterEvent[string]("time")
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
	var settingsService *wails.SettingsService
	var artworkCache domain.ArtworkCache
	var (
		urlQueue      []string
		urlQueueMu    sync.Mutex
		appReady      bool
		lastfmService *wails.LastFmService
		wailsApp      *application.App
	)

	handleURL := func(urlStr string) {
		urlStr = strings.Trim(urlStr, "\"' ")
		if urlStr == "" {
			return
		}

		// Handle cases where the URL might be passed with a flag or as part of a longer string
		if !strings.HasPrefix(urlStr, "airmedy://") {
			// Try to find the protocol link within the string
			idx := strings.Index(urlStr, "airmedy://")
			if idx != -1 {
				urlStr = urlStr[idx:]
			} else {
				return
			}
		}

		urlQueueMu.Lock()
		if !appReady || lastfmService == nil {
			slog.Info("queueing URL for later processing", "url", urlStr)
			urlQueue = append(urlQueue, urlStr)
			urlQueueMu.Unlock()
			return
		}
		urlQueueMu.Unlock()

		slog.Info("processing URL", "url", urlStr)
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
	}

	processQueue := func() {
		urlQueueMu.Lock()
		queue := urlQueue
		urlQueue = nil
		appReady = true
		urlQueueMu.Unlock()

		for _, urlStr := range queue {
			handleURL(urlStr)
		}
	}

	fxApp := fx.New(
		app.Module,
		fx.Populate(&greetService, &libraryService, &playerService, &searchService, &playlistService, &lyricsService, &eqService, &windowService, &settingsService, &lastfmService, &artworkCache),
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
				slog.Info("SingleInstance: second instance detected", "count", len(data.Args))
				for _, arg := range data.Args {
					cleanArg := strings.Trim(arg, "\"' ")
					if strings.Contains(cleanArg, "airmedy://") {
						slog.Info("SingleInstance: found deep link in arg", "arg", cleanArg)
						handleURL(cleanArg)
					}
				}
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
		appMenu := menu.AddSubmenu("Airmedy")
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

	mainWindow = wailsApp.Window.NewWithOptions(application.WebviewWindowOptions{
		Title:              "Airmedy",
		Width:              1280,
		Height:             800,
		MinWidth:           1060,
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

	// Process initial arguments
	for _, arg := range os.Args {
		cleanArg := strings.Trim(arg, "\"' ")
		if strings.Contains(cleanArg, "airmedy://") {
			handleURL(cleanArg)
		}
	}

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

	// Process any URLs that came in during start
	processQueue()

	// Handle deep links (e.g. airmedy://auth?token=...)
	wailsApp.Event.OnApplicationEvent(events.Common.ApplicationLaunchedWithUrl, func(e *application.ApplicationEvent) {
		handleURL(e.Context().URL())
	})

	// Cmd+Q fires ApplicationWillTerminate, bypassing WindowClosing on macOS.
	wailsApp.Event.OnApplicationEvent(events.Mac.ApplicationWillTerminate, func(_ *application.ApplicationEvent) {
		stopFX()
	})

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

	trayManager := wails.NewTrayManager(wailsApp, playerService.GetService(), libraryService)
	trayManager.Setup(systemTray, mainWindow)

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
