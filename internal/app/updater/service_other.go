//go:build !darwin && !windows

package updater

import (
	"bytes"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"

	update "github.com/inconshreveable/go-update"
)

func postUpdate(_ string, _ string) error {
	return nil
}

func getBundlePath(_ string) string {
	return ""
}

// applyUpdate replaces the binary in place when the install dir is writable
// (e.g. a user-local tar.gz install). System packages install to a root-owned
// path (/usr/local/bin); there the new binary is staged and installed with
// elevated privileges on restart via pkexec.
func (s *Service) applyUpdate(archiveData []byte, assetURL, exe string) (string, error) {
	binary, err := extractBinary(archiveData, assetURL, "airmedy")
	if err != nil {
		return "", fmt.Errorf("extract binary: %w", err)
	}

	if dirWritable(filepath.Dir(exe)) {
		if err := update.Apply(bytes.NewReader(binary), update.Options{TargetPath: exe}); err != nil {
			return "", fmt.Errorf("apply binary: %w", err)
		}
		return "", nil
	}

	staged, err := os.CreateTemp("", "airmedy-update-*")
	if err != nil {
		return "", fmt.Errorf("create staged binary: %w", err)
	}
	if _, err := staged.Write(binary); err != nil {
		_ = staged.Close()
		return "", fmt.Errorf("write staged binary: %w", err)
	}
	if err := staged.Close(); err != nil {
		return "", fmt.Errorf("close staged binary: %w", err)
	}
	if err := os.Chmod(staged.Name(), 0o755); err != nil {
		return "", fmt.Errorf("chmod staged binary: %w", err)
	}

	s.logger.Info("staged binary for elevated install", "path", staged.Name())
	return staged.Name(), nil
}

// dirWritable reports whether the current process can create files in dir.
func dirWritable(dir string) bool {
	f, err := os.CreateTemp(dir, ".airmedy-wtest-*")
	if err != nil {
		return false
	}
	name := f.Name()
	_ = f.Close()
	_ = os.Remove(name)
	return true
}

// relaunch installs the staged binary (elevated, if needed) once the current
// process has exited, then relaunches the app as the normal user.
func (s *Service) relaunch(_, exe string, pid int) {
	staged := s.stagingPath
	if staged == "" {
		if exe != "" {
			_ = exec.Command(exe).Start()
		}
		return
	}

	script := fmt.Sprintf(
		`while kill -0 %d 2>/dev/null; do sleep 0.1; done
pkexec sh -c 'cp -f %q %q && chmod 755 %q'
setsid %q >/dev/null 2>&1 &`,
		pid, staged, exe, exe, exe,
	)
	cmd := exec.Command("sh", "-c", script)
	_ = cmd.Start()
}
