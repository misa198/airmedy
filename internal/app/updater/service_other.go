//go:build !darwin

package updater

func postUpdate(_ string, _ string) error {
	return nil
}

func getBundlePath(_ string) string {
	return ""
}

func restartWithCodesign(_, _ string, _ int) {}

func (s *Service) applyUpdate(archiveData []byte, assetURL, exe string) (string, error) {
	return "", applyBinaryUpdate(archiveData, assetURL, exe)
}
