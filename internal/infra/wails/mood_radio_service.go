package wails

import (
	"context"
	"sync"

	"airmedy/internal/app/moodradio"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

// MoodRadioService is the Wails binding for Mood Radio's queue generation.
type MoodRadioService struct {
	service        *moodradio.Service
	trackQueryRepo domain.TrackQueryRepository
	mu             sync.RWMutex
	active         bool
}

func NewMoodRadioService(service *moodradio.Service, trackQueryRepo domain.TrackQueryRepository) *MoodRadioService {
	return &MoodRadioService{service: service, trackQueryRepo: trackQueryRepo}
}

func (s *MoodRadioService) GenerateMoodRadio(seedTrackID string, excludeTrackIDs []string, limit int) ([]*domain.TrackDTO, error) {
	return s.service.Generate(context.Background(), seedTrackID, excludeTrackIDs, limit)
}

// GetMoodRadioActive returns the process-wide Mood Radio state shared by all windows.
func (s *MoodRadioService) GetMoodRadioActive() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.active
}

// SetMoodRadioActive updates the shared Mood Radio state and notifies every webview.
func (s *MoodRadioService) SetMoodRadioActive(active bool) {
	s.mu.Lock()
	if s.active == active {
		s.mu.Unlock()
		return
	}
	s.active = active
	s.mu.Unlock()

	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("mood-radio:state", active)
	}
}

// GetMoodDensityGrid buckets analyzed tracks into a gridSize x gridSize
// energy/danceability grid for the Mood Playlist heatmap.
func (s *MoodRadioService) GetMoodDensityGrid(gridSize int) (*domain.MoodDensityGrid, error) {
	return s.trackQueryRepo.MoodDensityGrid(context.Background(), gridSize)
}
