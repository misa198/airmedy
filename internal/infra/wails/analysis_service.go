package wails

import (
	"context"

	"airmedy/internal/app/analysis"
	"airmedy/internal/app/player"
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
