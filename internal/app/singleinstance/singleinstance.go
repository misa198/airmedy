// Package singleinstance provides a cross-platform single-instance guard with
// argument relay over a loopback TCP socket.
//
// It must run before the application acquires any exclusive resources (the
// bleve index lock, the remote-server port, etc.). A second process detects the
// running primary, forwards its os.Args (which on Windows/Linux contains the
// deep-link URL), and exits — instead of booting a full second instance that
// would deadlock on those resources.
package singleinstance

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"hash/fnv"
	"net"
	"strings"
	"time"
)

// ErrAlreadyRunning is returned by Acquire when another instance owns the lock
// and this process's args were forwarded to it. The caller should exit.
var ErrAlreadyRunning = errors.New("another instance is already running")

// magic identifies our own protocol so a foreign listener on the same port does
// not make us exit silently.
const magic = "AIRMEDY_SI_V1\n"

const ack = "OK\n"

// Instance is the primary instance's listener. Keep it alive for the whole app
// lifetime and read Messages to react to later launches.
type Instance struct {
	ln   net.Listener
	msgs chan []string
}

// Messages yields the args of every later second instance that handed off to us.
func (i *Instance) Messages() <-chan []string { return i.msgs }

// Close stops the listener.
func (i *Instance) Close() error { return i.ln.Close() }

// PortForID derives a stable loopback port in the private range from a unique ID.
func PortForID(id string) int {
	h := fnv.New32a()
	_, _ = h.Write([]byte(id))
	return 49152 + int(h.Sum32()%16000)
}

// Acquire tries to become the primary instance on the given loopback port.
//
//   - primary:      returns a non-nil *Instance, nil error.
//   - second:       forwards args to the primary, returns nil, ErrAlreadyRunning.
//   - port taken by
//     a foreign app: returns nil, nil — caller proceeds best-effort.
func Acquire(port int, args []string) (*Instance, error) {
	addr := fmt.Sprintf("127.0.0.1:%d", port)

	ln, err := net.Listen("tcp", addr)
	if err == nil {
		inst := &Instance{ln: ln, msgs: make(chan []string, 16)}
		go inst.acceptLoop()
		return inst, nil
	}

	// Port busy: try to hand off to a running primary.
	if relayToPrimary(addr, args) {
		return nil, ErrAlreadyRunning
	}
	// Could not confirm one of our instances (foreign listener). Proceed without
	// the guard rather than refusing to start.
	return nil, nil
}

func (i *Instance) acceptLoop() {
	for {
		conn, err := i.ln.Accept()
		if err != nil {
			return
		}
		go i.handleConn(conn)
	}
}

func (i *Instance) handleConn(conn net.Conn) {
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(5 * time.Second))

	r := bufio.NewReader(conn)
	line, err := r.ReadString('\n')
	if err != nil || line != magic {
		return
	}

	var args []string
	if err := json.NewDecoder(r).Decode(&args); err != nil {
		return
	}
	_, _ = conn.Write([]byte(ack))

	select {
	case i.msgs <- args:
	default: // drop if nobody is consuming yet
	}
}

func relayToPrimary(addr string, args []string) bool {
	conn, err := net.DialTimeout("tcp", addr, 2*time.Second)
	if err != nil {
		return false
	}
	defer conn.Close()
	_ = conn.SetDeadline(time.Now().Add(2 * time.Second))

	if _, err := conn.Write([]byte(magic)); err != nil {
		return false
	}
	if err := json.NewEncoder(conn).Encode(args); err != nil {
		return false
	}

	line, err := bufio.NewReader(conn).ReadString('\n')
	return err == nil && strings.TrimSpace(line) == strings.TrimSpace(ack)
}
