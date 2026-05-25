//go:build darwin

package updater

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func makeAppBundle(t *testing.T) (bundlePath, exePath string) {
	t.Helper()
	root := t.TempDir()
	bundlePath = filepath.Join(root, "Airmedy.app")
	exePath = filepath.Join(bundlePath, "Contents", "MacOS", "airmedy")
	if err := os.MkdirAll(filepath.Dir(exePath), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(exePath, []byte{}, 0o755); err != nil {
		t.Fatal(err)
	}
	return bundlePath, exePath
}

func TestGetBundlePath(t *testing.T) {
	bundlePath, exePath := makeAppBundle(t)

	got := getBundlePath(exePath)
	if got != bundlePath {
		t.Fatalf("getBundlePath = %q, want %q", got, bundlePath)
	}
	if !strings.HasSuffix(got, ".app") {
		t.Fatalf("result %q does not end with .app", got)
	}
}

func TestGetBundlePath_NonBundle(t *testing.T) {
	dir := t.TempDir()
	exe := filepath.Join(dir, "airmedy")
	if err := os.WriteFile(exe, []byte{}, 0o755); err != nil {
		t.Fatal(err)
	}
	if got := getBundlePath(exe); got != "" {
		t.Fatalf("expected empty string for non-bundle exe, got %q", got)
	}
}

func TestPostUpdate_UpdatesPlist(t *testing.T) {
	bundlePath, exePath := makeAppBundle(t)

	plist := filepath.Join(bundlePath, "Contents", "Info.plist")
	plistContent := `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleShortVersionString</key>
	<string>0.0.1</string>
	<key>CFBundleVersion</key>
	<string>0.0.1</string>
</dict>
</plist>`
	if err := os.WriteFile(plist, []byte(plistContent), 0o644); err != nil {
		t.Fatal(err)
	}

	if err := postUpdate(exePath, "0.0.2"); err != nil {
		t.Fatalf("postUpdate failed: %v", err)
	}

	data, err := os.ReadFile(plist)
	if err != nil {
		t.Fatal(err)
	}
	content := string(data)
	if !strings.Contains(content, "0.0.2") {
		t.Fatalf("Info.plist not updated: still contains old version\n%s", content)
	}
	if strings.Contains(content, ">0.0.1<") {
		t.Fatalf("Info.plist still has old version 0.0.1\n%s", content)
	}
}
