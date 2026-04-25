package playlist

import (
	"context"
	"fmt"
	"io/ioutil"
	"log/slog"
	"mime"
	"os"
	"path/filepath"
	"time"

	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"

	"github.com/google/uuid"
)

type PlaylistService struct {
	repo         domain.PlaylistRepository
	artworkCache domain.ArtworkCache
	logger       *slog.Logger
}

func NewPlaylistService(repo domain.PlaylistRepository, artworkCache domain.ArtworkCache, logger *slog.Logger) *PlaylistService {
	return &PlaylistService{
		repo:         repo,
		artworkCache: artworkCache,
		logger:       logger,
	}
}

func (s *PlaylistService) Create(ctx context.Context, name, description string) (*domain.Playlist, error) {
	if name == "" {
		return nil, fmt.Errorf("playlist name cannot be empty")
	}
	p := &domain.Playlist{
		ID:          uuid.New().String(),
		Name:        name,
		Description: description,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
	}
	if err := s.repo.Save(ctx, p); err != nil {
		return nil, err
	}
	return p, nil
}

func (s *PlaylistService) Update(ctx context.Context, id, name, description string) error {
	if name == "" {
		return fmt.Errorf("playlist name cannot be empty")
	}
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", id)
	}
	p.Name = name
	p.Description = description
	return s.repo.Update(ctx, p)
}

func (s *PlaylistService) Delete(ctx context.Context, id string) error {
	return s.repo.Delete(ctx, id)
}

func (s *PlaylistService) GetAll(ctx context.Context) ([]*domain.Playlist, error) {
	return s.repo.GetAll(ctx)
}

func (s *PlaylistService) GetByID(ctx context.Context, id string) (*domain.Playlist, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *PlaylistService) GetTracks(ctx context.Context, playlistID string) ([]*domain.TrackDTO, error) {
	return s.repo.GetTracks(ctx, playlistID)
}

func (s *PlaylistService) AddTrack(ctx context.Context, playlistID, trackID string) error {
	count, err := s.repo.CountTracks(ctx, playlistID)
	if err != nil {
		return err
	}
	return s.repo.AddTrack(ctx, playlistID, trackID, count)
}

func (s *PlaylistService) RemoveTrack(ctx context.Context, playlistID, trackID string) error {
	return s.repo.RemoveTrack(ctx, playlistID, trackID)
}

func (s *PlaylistService) SetArtwork(ctx context.Context, id, imagePath string) (*string, error) {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return nil, fmt.Errorf("playlist not found: %s", id)
	}

	data, err := ioutil.ReadFile(imagePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read image: %w", err)
	}

	ext := filepath.Ext(imagePath)
	mimeType := mime.TypeByExtension(ext)
	if mimeType == "" {
		mimeType = "image/jpeg"
	}

	key, err := s.artworkCache.Save(ctx, data, mimeType)
	if err != nil {
		return nil, fmt.Errorf("failed to save artwork: %w", err)
	}

	p.ArtworkKey = &key
	if err := s.repo.Update(ctx, p); err != nil {
		return nil, err
	}

	return &key, nil
}

func (s *PlaylistService) RemoveArtwork(ctx context.Context, id string) error {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", id)
	}

	p.ArtworkKey = nil
	return s.repo.Update(ctx, p)
}

func (s *PlaylistService) GetPlaylistColors(ctx context.Context, id string) (*domain.ThemeColors, error) {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if p == nil || p.ArtworkKey == nil || *p.ArtworkKey == "" {
		return nil, nil
	}

	path := s.artworkCache.GetPath(*p.ArtworkKey)
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil, nil
	}

	colors, err := artwork.ExtractPalette(path)
	if err != nil {
		return nil, fmt.Errorf("failed to extract palette: %w", err)
	}

	return colors, nil
}
