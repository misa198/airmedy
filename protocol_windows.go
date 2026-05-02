//go:build windows

package main

import (
	"fmt"
	"os"
	"path/filepath"

	"golang.org/x/sys/windows/registry"
)

func registerProtocol() error {
	execPath, err := os.Executable()
	if err != nil {
		return err
	}
	execPath, err = filepath.Abs(execPath)
	if err != nil {
		return err
	}

	scheme := "airmedy"
	description := "URL:Airmedy Protocol"
	command := fmt.Sprintf("\"%s\" \"%%1\"", execPath)

	k, _, err := registry.CreateKey(registry.CURRENT_USER, `Software\Classes\`+scheme, registry.ALL_ACCESS)
	if err != nil {
		return err
	}
	defer k.Close()

	if err := k.SetStringValue("", description); err != nil {
		return err
	}
	if err := k.SetStringValue("URL Protocol", ""); err != nil {
		return err
	}

	sk, _, err := registry.CreateKey(k, `shell\open\command`, registry.ALL_ACCESS)
	if err != nil {
		return err
	}
	defer sk.Close()

	if err := sk.SetStringValue("", command); err != nil {
		return err
	}

	return nil
}
