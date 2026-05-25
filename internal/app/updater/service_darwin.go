package updater

import (
	"archive/zip"
	"bytes"
	"fmt"
	"io"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"strings"

	update "github.com/inconshreveable/go-update"
)

func applyUpdate(logger *slog.Logger, archiveData []byte, assetURL string) error {
	exe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("get executable path: %w", err)
	}

	bundlePath := getBundlePath(exe)
	if bundlePath == "" {
		logger.Info("not running from a bundle, falling back to binary update")
		binary, err := ExtractBinary(archiveData, assetURL, "airmedy")
		if err != nil {
			return fmt.Errorf("extract binary: %w", err)
		}
		return update.Apply(bytes.NewReader(binary), update.Options{TargetPath: exe})
	}

	logger.Info("performing bundle-level update", "bundle", bundlePath)

	// 1. Create a temporary directory for extraction
	tmpDir, err := os.MkdirTemp("", "airmedy-bundle-update-*")
	if err != nil {
		return fmt.Errorf("create temp dir: %w", err)
	}
	defer os.RemoveAll(tmpDir)

	// 2. Extract the whole zip (assuming it contains the .app bundle)
	if err := extractZipToDir(archiveData, tmpDir); err != nil {
		return fmt.Errorf("extract zip to dir: %w", err)
	}

	// 3. Find the .app bundle in the extracted files
	entries, err := os.ReadDir(tmpDir)
	if err != nil {
		return fmt.Errorf("read temp dir: %w", err)
	}
	var newBundlePath string
	for _, entry := range entries {
		if strings.HasSuffix(entry.Name(), ".app") {
			newBundlePath = filepath.Join(tmpDir, entry.Name())
			break
		}
	}

	if newBundlePath == "" {
		return fmt.Errorf("could not find .app bundle in downloaded archive")
	}

	// 4. Atomic-ish swap: move current bundle to .old, move new bundle to original path
	oldBundlePath := bundlePath + ".old"
	_ = os.RemoveAll(oldBundlePath) // Cleanup previous failed attempts

	if err := os.Rename(bundlePath, oldBundlePath); err != nil {
		return fmt.Errorf("move current bundle to .old: %w", err)
	}

	if err := os.Rename(newBundlePath, bundlePath); err != nil {
		// Try to rollback
		_ = os.Rename(oldBundlePath, bundlePath)
		return fmt.Errorf("move new bundle to target: %w", err)
	}

	// We'll leave the .old bundle for the OS to cleanup or we can try a best-effort delete
	// But since we are likely running from it, it might be busy.
	logger.Info("bundle swap successful", "old", oldBundlePath)
	return nil
}

func extractZipToDir(data []byte, targetDir string) error {
	r, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		return err
	}

	for _, f := range r.File {
		fpath := filepath.Join(targetDir, f.Name)

		if !strings.HasPrefix(fpath, filepath.Clean(targetDir)+string(os.PathSeparator)) {
			return fmt.Errorf("illegal file path: %s", fpath)
		}

		if f.FileInfo().IsDir() {
			_ = os.MkdirAll(fpath, os.ModePerm)
			continue
		}

		if err := os.MkdirAll(filepath.Dir(fpath), os.ModePerm); err != nil {
			return err
		}

		outFile, err := os.OpenFile(fpath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, f.Mode())
		if err != nil {
			return err
		}

		rc, err := f.Open()
		if err != nil {
			outFile.Close()
			return err
		}

		_, err = io.Copy(outFile, rc)
		outFile.Close()
		rc.Close()

		if err != nil {
			return err
		}
	}
	return nil
}

// postUpdate is called after the binary is swapped.
// On macOS, we MUST NOT modify Info.plist because it breaks the app signature,
// which causes the OS to revoke TCC permissions (Documents, etc.).
func postUpdate(exe, newVersion string) error {
	return nil
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
