package app

import (
	"context"
	"fmt"
	"os"
	"path/filepath"

	"changeme/internal/domain"
	"changeme/internal/infra/artwork"
	"changeme/internal/infra/bleve"
	"changeme/internal/infra/metadata"
	"changeme/internal/infra/sqlite"

	"github.com/adrg/xdg"
)

type App struct {
	DB                *sqlite.DB
	TrackRepo         domain.TrackRepository
	AlbumRepo         domain.AlbumRepository
	ArtistRepo        domain.ArtistRepository
	GenreRepo         domain.GenreRepository
	ComposerRepo      domain.ComposerRepository
	PlaylistRepo      domain.PlaylistRepository
	LyricRepo         domain.LyricRepository
	SearchService     domain.SearchService
	MetadataExtractor domain.MetadataExtractor
	ArtworkCache      domain.ArtworkCache
}

func NewApp() (*App, error) {
	dataDir := filepath.Join(xdg.DataHome, "airmedy")
	if err := os.MkdirAll(dataDir, 0755); err != nil {
		return nil, fmt.Errorf("failed to create data directory: %w", err)
	}

	dbPath := filepath.Join(dataDir, "airmedy.db")
	db, err := sqlite.NewDB(dbPath)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize database: %w", err)
	}

	indexPath := filepath.Join(dataDir, "airmedy.bleve")
	searchService, err := bleve.NewBleveSearchService(indexPath)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize search service: %w", err)
	}

	artworkCachePath := filepath.Join(dataDir, "artwork")
	artworkCache, err := artwork.NewDiskArtworkCache(artworkCachePath)
	if err != nil {
		return nil, fmt.Errorf("failed to initialize artwork cache: %w", err)
	}

	return &App{
		DB:                db,
		TrackRepo:         sqlite.NewTrackRepository(db),
		AlbumRepo:         sqlite.NewAlbumRepository(db),
		ArtistRepo:        sqlite.NewArtistRepository(db),
		GenreRepo:         sqlite.NewGenreRepository(db),
		ComposerRepo:      sqlite.NewComposerRepository(db),
		PlaylistRepo:      sqlite.NewPlaylistRepository(db),
		LyricRepo:         sqlite.NewLyricRepository(db),
		SearchService:     searchService,
		MetadataExtractor: metadata.NewTagLibExtractor(),
		ArtworkCache:      artworkCache,
	}, nil
}

func (a *App) Close() error {
	if err := a.SearchService.Close(); err != nil {
		return err
	}
	return a.DB.Close()
}

func (a *App) Context() context.Context {
	return context.Background()
}
