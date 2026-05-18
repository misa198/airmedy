package updater

import (
	"fmt"
	"os/exec"
	"path/filepath"
	"strings"
)

func postUpdate(exe string, newVersion string) error {
	macosDir := filepath.Dir(exe)
	if filepath.Base(macosDir) != "MacOS" {
		return nil
	}
	contentsDir := filepath.Dir(macosDir)
	bundlePath := filepath.Dir(contentsDir)
	if !strings.HasSuffix(bundlePath, ".app") {
		return nil
	}

	infoPlistPath := filepath.Join(contentsDir, "Info.plist")

	if err := exec.Command("plutil", "-replace", "CFBundleShortVersionString", "-string", newVersion, infoPlistPath).Run(); err != nil {
		return fmt.Errorf("failed to update CFBundleShortVersionString: %w", err)
	}
	if err := exec.Command("plutil", "-replace", "CFBundleVersion", "-string", newVersion, infoPlistPath).Run(); err != nil {
		return fmt.Errorf("failed to update CFBundleVersion: %w", err)
	}

	if err := exec.Command("codesign", "--force", "--deep", "--sign", "-", bundlePath).Run(); err != nil {
		return fmt.Errorf("failed to re-codesign bundle: %w", err)
	}

	return nil
}

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
