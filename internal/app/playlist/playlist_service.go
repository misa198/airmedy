package playlist

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"changeme/internal/domain"

	"github.com/google/uuid"
)

type PlaylistService struct {
	repo   domain.PlaylistRepository
	logger *slog.Logger
}

func NewPlaylistService(repo domain.PlaylistRepository, logger *slog.Logger) *PlaylistService {
	return &PlaylistService{repo: repo, logger: logger}
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
