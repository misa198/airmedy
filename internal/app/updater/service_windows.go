//go:build windows

package updater

import (
	"archive/zip"
	"bytes"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func postUpdate(_ string, _ string) error {
	return nil
}

func getBundlePath(_ string) string {
	return ""
}

// applyUpdate stages the NSIS installer extracted from the release zip. The
// app installs under Program Files (admin), so a normal-user process cannot
// swap the running .exe in place; the installer is run elevated on restart
// instead. Returns the staged installer path.
func (s *Service) applyUpdate(archiveData []byte, _, _ string) (string, error) {
	installer, err := extractInstaller(archiveData)
	if err != nil {
		return "", fmt.Errorf("extract installer: %w", err)
	}

	staged, err := os.CreateTemp("", "airmedy-installer-*.exe")
	if err != nil {
		return "", fmt.Errorf("create staged installer: %w", err)
	}
	if _, err := staged.Write(installer); err != nil {
		_ = staged.Close()
		return "", fmt.Errorf("write staged installer: %w", err)
	}
	if err := staged.Close(); err != nil {
		return "", fmt.Errorf("close staged installer: %w", err)
	}

	s.logger.Info("staged windows installer", "path", staged.Name())
	return staged.Name(), nil
}

// extractInstaller pulls the installer .exe out of the release zip.
func extractInstaller(data []byte) ([]byte, error) {
	r, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return nil, err
	}
	for _, f := range r.File {
		if f.FileInfo().IsDir() {
			continue
		}
		name := strings.ToLower(filepath.Base(f.Name))
		if strings.HasSuffix(name, ".exe") && strings.Contains(name, "installer") {
			rc, err := f.Open()
			if err != nil {
				return nil, err
			}
			defer func() { _ = rc.Close() }()
			return io.ReadAll(rc)
		}
	}
	return nil, fmt.Errorf("installer .exe not found in zip")
}

// relaunch waits for the current process to exit, runs the staged installer
// silently with elevation (UAC), then relaunches the app.
func (s *Service) relaunch(_, exe string, pid int) {
	installer := s.stagingPath
	if installer == "" {
		// Nothing staged — just relaunch the current exe.
		if exe != "" {
			_ = exec.Command(exe).Start()
		}
		return
	}

	script := fmt.Sprintf(
		"$ErrorActionPreference='SilentlyContinue';"+
			"Wait-Process -Id %d;"+
			"Start-Process -FilePath %q -ArgumentList '/S' -Verb RunAs -Wait;"+
			"Start-Process -FilePath %q",
		pid, installer, exe,
	)
	cmd := exec.Command("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", script)
	_ = cmd.Start()
}
