package main

import (
	"context"
	"embed"
	_ "embed"
	"log"
	"runtime"
	"sync"
	"time"

	"airmedy/internal/app"
	"airmedy/internal/domain"
	"airmedy/internal/infra/wails"

	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/wailsapp/wails/v3/pkg/events"
	"github.com/wailsapp/wails/v3/pkg/icons"
	"go.uber.org/fx"
)

//go:embed all:frontend/dist
var assets embed.FS

func init() {
	application.RegisterEvent[string]("time")
}

func main() {
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

	fxApp := fx.New(
		app.Module,
		fx.Populate(&greetService, &libraryService, &playerService, &searchService, &playlistService, &lyricsService, &eqService, &windowService, &settingsService, &artworkCache),
		fx.NopLogger, // Keep logs clean for now
	)

	startCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := fxApp.Start(startCtx); err != nil {
		log.Fatal(err)
	}

	wailsApp := application.New(application.Options{
		Name:        "airmedy",
		Description: "A modern music player",
		Services: []application.Service{
			application.NewService(greetService),
			application.NewService(libraryService),
			application.NewService(playerService),
			application.NewService(searchService),
			application.NewService(playlistService),
			application.NewService(lyricsService),
			application.NewService(eqService),
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
		menu.AddRole(application.EditMenu)
		menu.AddRole(application.ViewMenu)
		menu.AddRole(application.WindowMenu)
		menu.AddRole(application.HelpMenu)
	} else {
		fileMenu := menu.AddSubmenu("File")
		fileMenu.Add("Settings...").
			SetAccelerator("Ctrl+,").
			OnClick(func(ctx *application.Context) {
				wailsApp.Event.Emit("open-settings")
			})
		fileMenu.AddSeparator()
		fileMenu.AddRole(application.Quit)

		menu.AddRole(application.EditMenu)
		menu.AddRole(application.ViewMenu)
		menu.AddRole(application.WindowMenu)
		menu.AddRole(application.HelpMenu)
	}
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

	// Cmd+Q fires ApplicationWillTerminate, bypassing WindowClosing.
	// Save state here before the process exits.
	wailsApp.Event.OnApplicationEvent(events.Mac.ApplicationWillTerminate, func(_ *application.ApplicationEvent) {
		stopFX()
	})

	systemTray := wailsApp.SystemTray.New()
	systemTray.SetTemplateIcon(icons.SystrayMacTemplate)
	systemTray.SetTooltip("Airmedy")
	trayMenu := application.NewMenu()
	trayMenu.Add("Show Airmedy").OnClick(func(_ *application.Context) {
		mainWindow.Show()
		mainWindow.Focus()
	})
	trayMenu.AddSeparator()
	trayMenu.Add("Quit").OnClick(func(_ *application.Context) {
		wailsApp.Quit()
	})
	systemTray.SetMenu(trayMenu)

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
