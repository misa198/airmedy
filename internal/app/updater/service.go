package updater

import (
	"context"
	"fmt"
	"log/slog"
	"os"

	"github.com/blang/semver"
	"github.com/rhysd/go-github-selfupdate/selfupdate"
)

type UpdateInfo struct {
	Version     string `json:"version"`
	ReleaseNotes string `json:"release_notes"`
	PublishedAt string `json:"published_at"`
}

type Service struct {
	currentVersion string
	repoOwner      string
	repoName       string
	logger         *slog.Logger
}

func NewService(version string, logger *slog.Logger) *Service {
	return &Service{
		currentVersion: version,
		repoOwner:      "misa198",
		repoName:       "airmedy",
		logger:         logger,
	}
}

func (s *Service) CheckForUpdate(ctx context.Context) (*UpdateInfo, error) {
	s.logger.Info("checking for updates", "current_version", s.currentVersion)
	
	latest, found, err := selfupdate.DetectLatest(s.repoOwner + "/" + s.repoName)
	if err != nil {
		return nil, fmt.Errorf("failed to detect latest version: %w", err)
	}

	if !found {
		return nil, nil
	}

	current, err := semver.Parse(s.currentVersion)
	if err != nil {
		// If current version is not a valid semver (e.g. dev), assume it's old or just skip
		s.logger.Warn("current version is not a valid semver", "version", s.currentVersion)
		return nil, nil
	}

	if latest.Version.GT(current) {
		return &UpdateInfo{
			Version:      latest.Version.String(),
			ReleaseNotes: latest.ReleaseNotes,
			PublishedAt:  latest.PublishedAt.String(),
		}, nil
	}

	return nil, nil
}

func (s *Service) DownloadAndApply(ctx context.Context) error {
	latest, found, err := selfupdate.DetectLatest(s.repoOwner + "/" + s.repoName)
	if err != nil {
		return fmt.Errorf("failed to detect latest version: %w", err)
	}

	if !found {
		return fmt.Errorf("no update found")
	}

	s.logger.Info("downloading and applying update", "version", latest.Version.String())

	exe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("failed to get executable path: %w", err)
	}

	if err := selfupdate.UpdateTo(latest.AssetURL, exe); err != nil {
		return fmt.Errorf("failed to apply update: %w", err)
	}

	newVersion := latest.Version.String()
	if err := postUpdate(exe, newVersion); err != nil {
		s.logger.Warn("post-update steps failed (restart to complete update)", "error", err)
	}

	s.logger.Info("update applied successfully, restart the application to use the new version")
	return nil
}

func (s *Service) GetRestartInfo() (bundlePath string, exe string, err error) {
	exe, err = os.Executable()
	if err != nil {
		return "", "", fmt.Errorf("failed to get executable path: %w", err)
	}
	bundlePath = getBundlePath(exe)
	return bundlePath, exe, nil
}

func (s *Service) GetCurrentVersion() string {
	return s.currentVersion
}
