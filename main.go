package main

import (
	"context"
	"embed"
	"io/fs"
	"log/slog"
	"net/url"
	"os"
	"runtime"
	"strings"
	"sync"
	"time"

	"airmedy/internal/app"
	"airmedy/internal/app/config"
	"airmedy/internal/app/i18n"
	"airmedy/internal/app/remoteserver"
	"airmedy/internal/app/singleinstance"
	"airmedy/internal/domain"
	"airmedy/internal/infra/logging"
	"airmedy/internal/infra/sysinfo"
	"airmedy/internal/infra/wails"
	"runtime/debug"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
	"github.com/wailsapp/wails/v3/pkg/icons"
	"go.uber.org/fx"
)

//go:embed all:frontend/dist
var assets embed.FS

//go:embed remote/dist
var remoteAssets embed.FS

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

const singleInstanceID = "me.misa198.airmedy"

func main() {
	debug.SetGCPercent(50)

	// Build the file logger before anything else (and before the fx graph) so
	// bootstrap logs and any startup failure are written to disk. On Windows GUI
	// builds there is no stderr, so the default slog logger is a black hole; if
	// an fx provider fails (e.g. an arch-specific CGO lib on Windows/arm64) we
	// would otherwise os.Exit(1) with no log file and no visible error.
	bootCfg, err := config.NewConfig()
	if err != nil {
		os.Exit(1)
	}
	logRotator, logger, err := logging.NewFileLogger(bootCfg)
	if err != nil {
		os.Exit(1)
	}
	defer func() { _ = logRotator.Close() }()

	sysinfo.RaiseFileDescriptorLimit(logger)

	// Single-instance guard must run before any exclusive resource is acquired
	// (the bleve index lock, the remote-server port). A second process forwards
	// its args — including the deep-link URL on Windows/Linux — and exits, rather
	// than booting a full instance that would deadlock on those resources.
	siInstance, err := singleinstance.Acquire(singleinstance.PortForID(singleInstanceID), os.Args)
	if err != nil {
		if err == singleinstance.ErrAlreadyRunning {
			slog.Info("another instance is running; forwarded args and exiting")
			return
		}
		slog.Warn("single instance guard unavailable, continuing", "error", err)
	}
	if siInstance != nil {
		defer func() { _ = siInstance.Close() }()
	}

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
	var normalizationService *wails.NormalizationService
	var analysisService *wails.AnalysisService
	var analyticsService *wails.AnalyticsService
	var windowService *wails.WindowService
	var i18nService *i18n.Service
	var settingsService *wails.SettingsService
	var updaterService *wails.UpdaterService
	var artworkCache domain.ArtworkCache
	var remoteServerService *wails.RemoteServerService
	var mobilePairingService *wails.MobilePairingService
	var mobileLibrarySyncService *wails.MobileLibrarySyncService
	var moodRadioService *wails.MoodRadioService
	var trackTransitionNotificationActivator domain.TrackTransitionNotificationActivator
	var (
		lastfmService *wails.LastFmService
		wailsApp      *application.App
	)

	slog.Info("Starting Airmedy", "version", config.Version)

	remoteFS, _ := fs.Sub(remoteAssets, "remote/dist")

	fxApp := fx.New(
		app.Module,
		// Supply the logger built before fx so the logging module wires rotation
		// onto the same instance instead of constructing its own.
		fx.Supply(logRotator, logger),
		fx.Provide(func() remoteserver.RemoteFS { return remoteserver.RemoteFS{FS: remoteFS} }),
		fx.Populate(&greetService, &libraryService, &playerService, &searchService, &playlistService, &lyricsService, &eqService, &normalizationService, &analysisService, &analyticsService, &windowService, &i18nService, &settingsService, &lastfmService, &updaterService, &artworkCache, &remoteServerService, &mobilePairingService, &mobileLibrarySyncService, &moodRadioService, &trackTransitionNotificationActivator),
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
		// Wails invokes OnShutdown synchronously from macOS'
		// applicationShouldTerminate callback. Unlike ApplicationWillTerminate
		// event listeners, this blocks Cmd+Q until the FX lifecycle has persisted
		// the current playback state.
		OnShutdown: stopFX,
		Services: []application.Service{
			application.NewService(greetService),
			application.NewService(libraryService),
			application.NewService(playerService),
			application.NewService(searchService),
			application.NewService(playlistService),
			application.NewService(lyricsService),
			application.NewService(eqService),
			application.NewService(normalizationService),
			application.NewService(analysisService),
			application.NewService(analyticsService),
			application.NewService(lastfmService),
			application.NewService(windowService),
			application.NewService(settingsService),
			application.NewService(updaterService),
			application.NewService(remoteServerService),
			application.NewService(mobilePairingService),
			application.NewService(mobileLibrarySyncService),
			application.NewService(moodRadioService),
		},
		Assets: application.AssetOptions{
			Handler: wails.NewAssetHandler(assets, artworkCache),
		},
		Mac: application.MacOptions{
			ApplicationShouldTerminateAfterLastWindowClosed: false,
		},
		Linux: application.LinuxOptions{
			// X11 fallback: sets g_set_prgname so the window's WM_CLASS is "airmedy",
			// matching StartupWMClass in org.wails.airmedy.desktop. On Wayland/modern
			// GNOME the dock icon is matched via the GApplication app_id
			// ("org.wails.airmedy"), which is why the .desktop file is named to match.
			ProgramName: "airmedy",
		},
	})

	// Initialize i18n
	settings, _ := settingsService.GetSettings(context.Background())
	i18nService.SetLocale(settings.Language)

	// Create application menu
	menu := wails.BuildAppMenu(wailsApp, i18nService, playerService)
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
		Windows: application.WindowsWindow{
			Theme:       winTheme(settings.Theme),
			CustomTheme: winCustomTheme(settings.Theme),
		},
		BackgroundColour: bgRGBA(settings.Theme),
		URL:              "/",
	})

	mainWindow.RegisterHook(events.Common.WindowClosing, func(e *application.WindowEvent) {
		mainWindow.Hide()
		e.Cancel()
	})
	windowService.SetMainWindow(mainWindow)
	trackTransitionNotificationActivator.SetTrackTransitionActivationCallback(func() {
		application.InvokeAsync(windowService.ShowMain)
	})
	windowService.SetTitleBarTheme(settings.Theme)
	mainWindow.Show()
	mainWindow.Focus()

	thumbBarMgr := wails.NewThumbBarManager(playerService.GetService())
	thumbBarMgr.Setup(mainWindow)

	// Wire the SMTC "Now Playing" card click to show the correct window.
	// windowService.ShowCurrent() focuses the mini player if open, otherwise
	// the main window — matching the tray "Show Airmedy" behaviour.
	playerService.GetService().SetNowPlayingActivateCallback(func() {
		application.InvokeAsync(func() {
			windowService.ShowCurrent()
		})
	})

	// Wails/AppKit can reset a floating window's level while macOS reactivates
	// the app from its Dock icon. Reapply the persisted mini-player pin state
	// after both activation and reopen, without changing the user's preference.
	if runtime.GOOS == "darwin" {
		restoreMiniPin := func(_ *application.ApplicationEvent) {
			application.InvokeAsync(windowService.RestoreMiniAlwaysOnTop)
		}
		wailsApp.Event.OnApplicationEvent(events.Mac.ApplicationDidBecomeActive, restoreMiniPin)
		wailsApp.Event.OnApplicationEvent(events.Mac.ApplicationShouldHandleReopen, restoreMiniPin)
	}

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
			Frameless:           runtime.GOOS == "windows",
			MinimiseButtonState: application.ButtonHidden,
			MaximiseButtonState: application.ButtonHidden,
			CloseButtonState:    application.ButtonHidden,
			Mac: application.MacWindow{
				InvisibleTitleBarHeight: 28,
				Backdrop:                application.MacBackdropTranslucent,
				TitleBar:                application.MacTitleBarHiddenInset,
				CollectionBehavior:      application.MacWindowCollectionBehaviorTransient,
			},
			Windows: application.WindowsWindow{
				Theme:       winTheme(settings.Theme),
				CustomTheme: winCustomTheme(settings.Theme),
			},
			BackgroundColour: bgRGBA(settings.Theme),
			URL:              "/?mode=mini#/mini-player",
		})
		wails.LockMiniPlayerSquare(w)
		// Hidden GTK windows have no X11 surface until shown, so retry once the
		// native surface exists. The macOS/Windows helpers are idempotent.
		w.RegisterHook(events.Common.WindowShow, func(_ *application.WindowEvent) {
			wails.LockMiniPlayerSquare(w)
		})
		// Restore saved geometry + pin mode, clamped to the current screen layout.
		windowService.ApplyMiniState(w)
		// Persist geometry as the user moves/resizes the window (debounced).
		w.RegisterHook(events.Common.WindowDidMove, func(_ *application.WindowEvent) {
			windowService.SaveMiniGeometry()
		})
		w.RegisterHook(events.Common.WindowDidResize, func(_ *application.WindowEvent) {
			windowService.SaveMiniGeometry()
		})
		w.RegisterHook(events.Common.WindowClosing, func(e *application.WindowEvent) {
			windowService.SaveMiniGeometry()
			windowService.OnMiniPlayerClosed()
			// No e.Cancel() — Wails destroys the window, freeing its memory
		})
		return w
	})

	// Handle deep links (e.g. airmedy://auth?token=...). Shared by the macOS
	// Apple-Event path and the Windows/Linux second-instance relay.
	handleDeepLink := func(urlStr string) {
		if urlStr == "" {
			return
		}
		u, err := url.Parse(urlStr)
		if err != nil {
			return
		}
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

	// macOS: URL-scheme launches arrive as an Apple Event on the running instance.
	wailsApp.Event.OnApplicationEvent(events.Common.ApplicationLaunchedWithUrl, func(e *application.ApplicationEvent) {
		handleDeepLink(e.Context().URL())
	})

	// Windows/Linux: a second process relays its args (incl. the deep-link URL).
	if siInstance != nil {
		go func() {
			for args := range siInstance.Messages() {
				if mainWindow != nil {
					mainWindow.Show()
					mainWindow.Focus()
				}
				for _, a := range args {
					if strings.HasPrefix(a, "airmedy://") {
						handleDeepLink(a)
					}
				}
			}
		}()
	}

	var trayManager *wails.TrayManager
	settings, err = settingsService.GetSettings(context.Background())
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

		trayManager = wails.NewTrayManager(wailsApp, playerService.GetService(), libraryService, i18nService, windowService)
		trayManager.Setup(systemTray, mainWindow)
	}

	// Listen for language changes
	wailsApp.Event.On("language:changed", func(event *application.CustomEvent) {
		if lang, ok := event.Data.(string); ok {
			i18nService.SetLocale(lang)
			application.InvokeSync(func() {
				newMenu := wails.BuildAppMenu(wailsApp, i18nService, playerService)
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

	thumbBarMgr.Stop()
	stopFX()
}

// bgRGBA returns the BackgroundColour matching the app theme's --bg-main value.
func bgRGBA(appTheme string) application.RGBA {
	switch appTheme {
	case "light":
		return application.NewRGB(244, 244, 245) // #f4f4f5
	case "black":
		return application.NewRGB(10, 10, 10) // #0a0a0a
	default: // "dark", "system"
		return application.NewRGB(24, 24, 27) // #18181b
	}
}

// winTheme returns the Wails Windows Theme constant for the given app theme.
func winTheme(appTheme string) application.Theme {
	switch appTheme {
	case "dark", "black":
		return application.Dark
	case "light":
		return application.Light
	default: // "system"
		return application.SystemDefault
	}
}

// winCustomTheme builds a ThemeSettings whose title bar colours match --bg-main.
// Colors are uint32 in 0x00BBGGRR format (DWM / Wails convention).
func winCustomTheme(appTheme string) application.ThemeSettings {
	ptr := func(v uint32) *uint32 { return &v }

	// dark / black palette
	darkBar := ptr(0x001B1818)  // #18181b
	darkText := ptr(0x00FFFFFF) // white
	if appTheme == "black" {
		darkBar = ptr(0x000A0A0A) // #0a0a0a
	}
	darkTheme := &application.WindowTheme{
		TitleBarColour:  darkBar,
		TitleTextColour: darkText,
		BorderColour:    darkBar,
	}

	// light palette
	lightBar := ptr(0x00F5F4F4)    // #f4f4f5
	lightText := ptr(0x000A0A0A)   // near-black
	lightBorder := ptr(0x00E5E4E4) // subtle border, slightly darker than bg
	lightTheme := &application.WindowTheme{
		TitleBarColour:  lightBar,
		TitleTextColour: lightText,
		BorderColour:    lightBorder,
	}

	return application.ThemeSettings{
		DarkModeActive:    darkTheme,
		DarkModeInactive:  darkTheme,
		LightModeActive:   lightTheme,
		LightModeInactive: lightTheme,
	}
}
