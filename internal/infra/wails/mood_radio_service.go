package wails

import (
	"context"

	"airmedy/internal/domain"
)

// MoodRadioService is the Wails binding for Mood Radio's "give me more like
// this" queue seeding, backed directly by TrackQueryRepository — thin
// enough that it doesn't warrant its own app-layer service.
type MoodRadioService struct {
	trackQueryRepo domain.TrackQueryRepository
}

func NewMoodRadioService(trackQueryRepo domain.TrackQueryRepository) *MoodRadioService {
	return &MoodRadioService{trackQueryRepo: trackQueryRepo}
}

func (s *MoodRadioService) SeedMoodRadio(seedTrackID string, limit int) ([]*domain.TrackDTO, error) {
	return s.trackQueryRepo.FindSimilar(context.Background(), seedTrackID, limit)
}

// GetMoodDensityGrid buckets analyzed tracks into a gridSize x gridSize
// energy/danceability grid for the Mood Playlist heatmap.
func (s *MoodRadioService) GetMoodDensityGrid(gridSize int) (*domain.MoodDensityGrid, error) {
	return s.trackQueryRepo.MoodDensityGrid(context.Background(), gridSize)
}
