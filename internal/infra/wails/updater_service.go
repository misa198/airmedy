package wails

import (
	"airmedy/internal/app/updater"
	"context"
	"os"
	"os/exec"

	"github.com/wailsapp/wails/v3/pkg/application"
)

type UpdaterService struct {
	svc *updater.Service
}

func NewUpdaterService(svc *updater.Service) *UpdaterService {
	return &UpdaterService{svc: svc}
}

func (s *UpdaterService) CheckForUpdate(ctx context.Context) (*updater.UpdateInfo, error) {
	return s.svc.CheckForUpdate(ctx)
}

func (s *UpdaterService) DownloadAndApply(ctx context.Context) error {
	return s.svc.DownloadAndApply(ctx)
}

func (s *UpdaterService) GetCurrentVersion() string {
	return s.svc.GetCurrentVersion()
}

// RestartApp relaunches the application and exits the current process.
func (s *UpdaterService) RestartApp() {
	bundlePath, exe, err := s.svc.GetRestartInfo()

	var cmd *exec.Cmd
	if err == nil && bundlePath != "" {
		cmd = exec.Command("open", bundlePath)
	} else if err == nil {
		cmd = exec.Command(exe)
	}

	if cmd != nil {
		_ = cmd.Start()
	}

	if app := application.Get(); app != nil {
		app.Quit()
	} else {
		os.Exit(0)
	}
}
