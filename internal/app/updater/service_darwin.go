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

// postUpdate updates Info.plist version strings after the binary is swapped in.
// Codesigning is deferred to restartWithCodesign so it runs after the process exits.
func postUpdate(exe, newVersion string) error {
	macosDir := filepath.Dir(exe)
	if filepath.Base(macosDir) != "MacOS" {
		return nil
	}
	contentsDir := filepath.Dir(macosDir)
	bundlePath := filepath.Dir(contentsDir)
	if !strings.HasSuffix(bundlePath, ".app") {
		return nil
	}

	plist := filepath.Join(contentsDir, "Info.plist")
	if err := exec.Command("plutil", "-replace", "CFBundleShortVersionString",
		"-string", newVersion, plist).Run(); err != nil {
		return fmt.Errorf("update CFBundleShortVersionString: %w", err)
	}
	if err := exec.Command("plutil", "-replace", "CFBundleVersion",
		"-string", newVersion, plist).Run(); err != nil {
		return fmt.Errorf("update CFBundleVersion: %w", err)
	}

	return nil
}

// applyUpdate extracts the full .app bundle from the zip archive to a staging
// path next to the current bundle. Returns the staging path.
func (s *Service) applyUpdate(archiveData []byte, assetURL, exe string) (stagingPath string, err error) {
	bundlePath := getBundlePath(exe)
	if bundlePath == "" {
		// Not inside a .app — fall back to binary-only update.
		return "", applyBinaryUpdate(archiveData, assetURL, exe)
	}
	stagingPath = bundlePath + ".update"
	if err := extractAppBundle(archiveData, stagingPath); err != nil {
		return "", fmt.Errorf("extract app bundle: %w", err)
	}
	s.logger.Info("staged full app bundle", "staging", stagingPath)
	return stagingPath, nil
}

// extractAppBundle extracts the first *.app directory from a zip archive to destPath.
func extractAppBundle(data []byte, destPath string) error {
	r, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return err
	}

	tmp, err := os.MkdirTemp("", "airmedy-bundle-*")
	if err != nil {
		return err
	}
	defer os.RemoveAll(tmp)

	for _, f := range r.File {
		target := filepath.Join(tmp, f.Name)
		if !strings.HasPrefix(filepath.Clean(target), filepath.Clean(tmp)) {
			return fmt.Errorf("zip path traversal: %s", f.Name)
		}
		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(target, f.Mode()); err != nil {
				return err
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(target), 0755); err != nil {
			return err
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.OpenFile(target, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, f.Mode())
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, rc)
		rc.Close()
		out.Close()
		if err != nil {
			return err
		}
	}

	// Find the .app directory in the extracted temp dir.
	entries, err := os.ReadDir(tmp)
	if err != nil {
		return err
	}
	var appDir string
	for _, e := range entries {
		if e.IsDir() && strings.HasSuffix(e.Name(), ".app") {
			appDir = filepath.Join(tmp, e.Name())
			break
		}
	}
	if appDir == "" {
		return fmt.Errorf("no .app bundle found in archive")
	}

	os.RemoveAll(destPath)
	return os.Rename(appDir, destPath)
}

// restartWithCodesign launches a background shell that waits for the current
// process to exit, replaces the bundle with the staged update (if any),
// ad-hoc signs it, removes quarantine, and reopens the app.
func restartWithCodesign(bundlePath, stagingPath string, pid int) {
	replaceCmd := ""
	if stagingPath != "" {
		replaceCmd = fmt.Sprintf("rm -rf %q && mv %q %q", bundlePath, stagingPath, bundlePath)
	}
	script := fmt.Sprintf(
		`while kill -0 %d 2>/dev/null; do sleep 0.1; done
%s
codesign --force --deep --sign - %q
xattr -d com.apple.quarantine %q 2>/dev/null || true
open %q`,
		pid, replaceCmd, bundlePath, bundlePath, bundlePath,
	)
	cmd := exec.Command("sh", "-c", script)
	_ = cmd.Start()
}

// getBundlePath returns the .app bundle path containing exe, or "" if not inside a bundle.
func getBundlePath(exe string) string {
	macosDir := filepath.Dir(exe)
	if filepath.Base(macosDir) != "MacOS" {
		return ""
	}
	contentsDir := filepath.Dir(macosDir)
	bundlePath := filepath.Dir(contentsDir)
	if !strings.HasSuffix(bundlePath, ".app") {
		return ""
	}
	return bundlePath
}
