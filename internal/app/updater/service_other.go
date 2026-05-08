//go:build !darwin

package updater

func postUpdate(_ string, _ string) error {
	return nil
}

func getBundlePath(_ string) string {
	return ""
}
