package wails

import (
	"context"

	"airmedy/internal/app/analysis"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"
)

type AnalysisService struct {
	service *analysis.AnalysisService
	player  *player.PlayerService
}

func NewAnalysisService(service *analysis.AnalysisService, player *player.PlayerService) *AnalysisService {
	return &AnalysisService{service: service, player: player}
}

// SetLibraryAnalysisEnabled starts/stops the background analysis worker pool
// and persists the choice. Disabling also force-disables Normalization
// (it depends on data this pipeline produces), so the player is told to
// reapply (clear) its preamp gain for the current track either way.
func (s *AnalysisService) SetLibraryAnalysisEnabled(enabled bool) error {
	if err := s.service.SetEnabled(context.Background(), enabled); err != nil {
		return err
	}
	s.player.ReapplyNormalization()
	return nil
}

// GetProgress returns a snapshot of analysis progress, for the frontend to
// fetch once on mount before subscribing to "analysis:progress" events.
func (s *AnalysisService) GetProgress() domain.AnalysisProgress {
	return s.service.GetProgress()
}

func (s *AnalysisService) ListFailedTracks() ([]domain.FailedAnalysisTrack, error) {
	return s.service.ListFailedTracks(context.Background())
}

func (s *AnalysisService) RetryFailedTracks() error {
	return s.service.RetryFailedTracks(context.Background())
}

// WorkerCountInfo is the current + max worker count, for the settings slider.
type WorkerCountInfo struct {
	Count int `json:"count"`
	Max   int `json:"max"`
}

// GetWorkerCountInfo returns the currently configured worker count and the
// maximum value the settings UI should allow (half the logical core count).
func (s *AnalysisService) GetWorkerCountInfo() WorkerCountInfo {
	count, max := s.service.GetWorkerCount(context.Background())
	return WorkerCountInfo{Count: count, Max: max}
}

// SetWorkerCount persists the desired concurrent-worker count and applies it
// live, restarting the pool if it's currently running.
func (s *AnalysisService) SetWorkerCount(count int) error {
	return s.service.SetWorkerCount(context.Background(), count)
}
