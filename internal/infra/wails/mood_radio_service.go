package wails

import (
	"context"

	"airmedy/internal/app/moodradio"
	"airmedy/internal/domain"
)

// MoodRadioService is the Wails binding for Mood Radio's queue generation.
type MoodRadioService struct {
	service        *moodradio.Service
	trackQueryRepo domain.TrackQueryRepository
}

func NewMoodRadioService(service *moodradio.Service, trackQueryRepo domain.TrackQueryRepository) *MoodRadioService {
	return &MoodRadioService{service: service, trackQueryRepo: trackQueryRepo}
}

func (s *MoodRadioService) GenerateMoodRadio(seedTrackID string, excludeTrackIDs []string, limit int) ([]*domain.TrackDTO, error) {
	return s.service.Generate(context.Background(), seedTrackID, excludeTrackIDs, limit)
}

// GetMoodDensityGrid buckets analyzed tracks into a gridSize x gridSize
// energy/danceability grid for the Mood Playlist heatmap.
func (s *MoodRadioService) GetMoodDensityGrid(gridSize int) (*domain.MoodDensityGrid, error) {
	return s.trackQueryRepo.MoodDensityGrid(context.Background(), gridSize)
}
