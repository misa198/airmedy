//go:build linux

package power

import (
	"airmedy/internal/domain"
	"fmt"
	"os"
	"sync"

	"github.com/godbus/dbus/v5"
)

type linuxInhibitor struct {
	mu sync.Mutex
	fd *os.File
}

func NewInhibitor() domain.SleepInhibitor {
	return &linuxInhibitor{}
}

func (i *linuxInhibitor) Inhibit() error {
	i.mu.Lock()
	defer i.mu.Unlock()
	if i.fd != nil {
		return nil
	}

	conn, err := dbus.ConnectSystemBus()
	if err != nil {
		return fmt.Errorf("dbus connect: %w", err)
	}
	defer func() { _ = conn.Close() }()

	obj := conn.Object("org.freedesktop.login1", "/org/freedesktop/login1")
	var fd dbus.UnixFD
	if err := obj.Call("org.freedesktop.login1.Manager.Inhibit", 0,
		"sleep", "Airmedy", "Music is playing", "block",
	).Store(&fd); err != nil {
		return fmt.Errorf("inhibit call: %w", err)
	}

	i.fd = os.NewFile(uintptr(fd), "inhibit-lock")
	return nil
}

func (i *linuxInhibitor) Release() error {
	i.mu.Lock()
	defer i.mu.Unlock()
	if i.fd == nil {
		return nil
	}
	err := i.fd.Close()
	i.fd = nil
	return err
}
