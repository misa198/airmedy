//go:build !darwin

package updater

import (
	"bytes"
	"fmt"
	"log/slog"
	"os"

	update "github.com/inconshreveable/go-update"
)

func applyUpdate(logger *slog.Logger, archiveData []byte, assetURL string) error {
	binary, err := ExtractBinary(archiveData, assetURL, "airmedy.exe") // windows
	if err != nil {
		// try linux
		binary, err = ExtractBinary(archiveData, assetURL, "airmedy")
	}
	if err != nil {
		return fmt.Errorf("extract binary: %w", err)
	}

	exe, err := os.Executable()
	if err != nil {
		return fmt.Errorf("get executable path: %w", err)
	}

	logger.Info("applying binary update", "target", exe)
	return update.Apply(bytes.NewReader(binary), update.Options{TargetPath: exe})
}

func postUpdate(_ string, _ string) error {
	return nil
}

func getBundlePath(_ string) string {
	return ""
}
