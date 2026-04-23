package main

import (
	"context"
	"embed"
	_ "embed"
	"log"
	"time"

	"changeme/internal/app"
	"changeme/internal/domain"
	"changeme/internal/infra/wails"

	"github.com/wailsapp/wails/v3/pkg/application"
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
	var artworkCache domain.ArtworkCache

	fxApp := fx.New(
		app.Module,
		fx.Populate(&greetService, &libraryService, &playerService, &searchService, &playlistService, &lyricsService, &eqService, &artworkCache),
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
		},
		Assets: application.AssetOptions{
			Handler: wails.NewAssetHandler(assets, artworkCache),
		},
		Mac: application.MacOptions{
			ApplicationShouldTerminateAfterLastWindowClosed: true,
		},
	})

	wailsApp.Window.NewWithOptions(application.WebviewWindowOptions{
		Title:     "Airmedy",
		Width:     1280,
		Height:    800,
		MinWidth:  1024,
		MinHeight: 768,
		Mac: application.MacWindow{
			InvisibleTitleBarHeight: 50,
			Backdrop:                application.MacBackdropTranslucent,
			TitleBar:                application.MacTitleBarHiddenInset,
		},
		BackgroundColour: application.NewRGB(27, 38, 54),
		URL:              "/",
	})

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

	stopCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := fxApp.Stop(stopCtx); err != nil {
		log.Fatal(err)
	}
}
