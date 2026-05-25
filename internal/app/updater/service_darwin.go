package updater

import (
	"fmt"
	"os/exec"
	"path/filepath"
	"strings"
)

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
