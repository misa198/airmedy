//go:build linux

package audio

import (
	"log/slog"
	"strings"
	"sync"

	"airmedy/internal/domain"

	"github.com/godbus/dbus/v5"
	"github.com/godbus/dbus/v5/introspect"
	"github.com/godbus/dbus/v5/prop"
)

const (
	mprisPath      = "/org/mpris/MediaPlayer2"
	mprisBusName   = "org.mpris.MediaPlayer2.airmedy"
	mprisRootIface = "org.mpris.MediaPlayer2"
	mprisPlrIface  = "org.mpris.MediaPlayer2.Player"
)

// mprisBackend exposes OS media controls on Linux via the MPRIS D-Bus spec,
// so GNOME/KDE shells, media keys and tools like playerctl can see and control
// playback.
type mprisBackend struct {
	logger *slog.Logger
	conn   *dbus.Conn
	props  *prop.Properties

	mu                          sync.Mutex
	play, pause, next, previous func()
	seek                        func(float64)
	state                       domain.PlaybackState
	positionUs                  int64
}

func newNowPlayingBackend(logger *slog.Logger) nowPlayingBackend {
	conn, err := dbus.ConnectSessionBus()
	if err != nil {
		logger.Warn("MPRIS: session bus unavailable; OS media controls disabled", "err", err)
		return nil
	}

	b := &mprisBackend{logger: logger, conn: conn, state: domain.PlaybackStateStopped}

	if err := conn.Export(&mprisRoot{b: b}, mprisPath, mprisRootIface); err != nil {
		logger.Warn("MPRIS: export root failed", "err", err)
		_ = conn.Close()
		return nil
	}
	if err := conn.Export(&mprisPlayer{b: b}, mprisPath, mprisPlrIface); err != nil {
		logger.Warn("MPRIS: export player failed", "err", err)
		_ = conn.Close()
		return nil
	}

	props, err := prop.Export(conn, mprisPath, b.propSpec())
	if err != nil {
		logger.Warn("MPRIS: export properties failed", "err", err)
		_ = conn.Close()
		return nil
	}
	b.props = props

	node := &introspect.Node{
		Name: mprisPath,
		Interfaces: []introspect.Interface{
			introspect.IntrospectData,
			prop.IntrospectData,
			mprisRootIntrospect,
			mprisPlayerIntrospect,
		},
	}
	if err := conn.Export(introspect.NewIntrospectable(node), mprisPath, "org.freedesktop.DBus.Introspectable"); err != nil {
		logger.Warn("MPRIS: export introspectable failed", "err", err)
	}

	reply, err := conn.RequestName(mprisBusName, dbus.NameFlagReplaceExisting)
	if err != nil {
		logger.Warn("MPRIS: request name failed", "err", err)
		_ = conn.Close()
		return nil
	}
	if reply != dbus.RequestNameReplyPrimaryOwner {
		logger.Warn("MPRIS: bus name already owned; OS media controls disabled")
		_ = conn.Close()
		return nil
	}

	return b
}

func (b *mprisBackend) propSpec() map[string]map[string]*prop.Prop {
	ro := func(v interface{}, emit prop.EmitType) *prop.Prop {
		return &prop.Prop{Value: v, Writable: false, Emit: emit, Callback: nil}
	}
	return map[string]map[string]*prop.Prop{
		mprisRootIface: {
			"CanQuit":             ro(false, prop.EmitConst),
			"CanRaise":            ro(true, prop.EmitConst),
			"HasTrackList":        ro(false, prop.EmitConst),
			"Identity":            ro("Airmedy", prop.EmitConst),
			"DesktopEntry":        ro("org.wails.airmedy", prop.EmitConst),
			"SupportedUriSchemes": ro([]string{}, prop.EmitConst),
			"SupportedMimeTypes":  ro([]string{}, prop.EmitConst),
		},
		mprisPlrIface: {
			"PlaybackStatus": ro("Stopped", prop.EmitTrue),
			"Metadata":       ro(map[string]dbus.Variant{}, prop.EmitTrue),
			"CanGoNext":      ro(true, prop.EmitConst),
			"CanGoPrevious":  ro(true, prop.EmitConst),
			"CanPlay":        ro(true, prop.EmitConst),
			"CanPause":       ro(true, prop.EmitConst),
			"CanSeek":        ro(true, prop.EmitConst),
			"CanControl":     ro(true, prop.EmitConst),
			"Position":       ro(int64(0), prop.EmitFalse),
			"Rate":           ro(1.0, prop.EmitConst),
			"MinimumRate":    ro(1.0, prop.EmitConst),
			"MaximumRate":    ro(1.0, prop.EmitConst),
			"Volume":         ro(1.0, prop.EmitFalse),
		},
	}
}

// --- nowPlayingBackend ---

func (b *mprisBackend) setupRemoteCommands() {} // name + handlers already registered in the constructor

func (b *mprisBackend) setRemoteCallbacks(play, pause, next, previous func(), seek func(float64)) {
	b.mu.Lock()
	defer b.mu.Unlock()
	b.play, b.pause, b.next, b.previous, b.seek = play, pause, next, previous, seek
}

func (b *mprisBackend) updateNowPlaying(track *domain.TrackDTO, position float64, artworkPath string) {
	if b.props == nil || track == nil {
		return
	}

	artist := ""
	if len(track.Artists) > 0 {
		artist = track.Artists[0].Name
	}
	album := ""
	if track.Album != nil {
		album = track.Album.Title
	}
	lengthUs := int64(float64(track.Duration) * 1e6)

	// Always set mpris:artUrl (empty when the track has no artwork); omitting it
	// lets some shells keep showing the previous track's art.
	artURL := ""
	if artworkPath != "" {
		artURL = "file://" + artworkPath
	}
	meta := map[string]dbus.Variant{
		"mpris:trackid": dbus.MakeVariant(trackObjectPath(track.ID)),
		"mpris:length":  dbus.MakeVariant(lengthUs),
		"mpris:artUrl":  dbus.MakeVariant(artURL),
		"xesam:title":   dbus.MakeVariant(track.Title),
		"xesam:artist":  dbus.MakeVariant([]string{artist}),
		"xesam:album":   dbus.MakeVariant(album),
	}

	b.mu.Lock()
	b.positionUs = int64(position * 1e6)
	b.mu.Unlock()

	b.props.SetMust(mprisPlrIface, "Metadata", meta)
	b.props.SetMust(mprisPlrIface, "Position", int64(position*1e6))
}

func (b *mprisBackend) updateNowPlayingPosition(position float64) {
	if b.props == nil {
		return
	}
	b.mu.Lock()
	b.positionUs = int64(position * 1e6)
	b.mu.Unlock()
	b.props.SetMust(mprisPlrIface, "Position", int64(position*1e6))
}

func (b *mprisBackend) setPlaybackState(state domain.PlaybackState) {
	if b.props == nil {
		return
	}
	b.mu.Lock()
	b.state = state
	b.mu.Unlock()
	b.props.SetMust(mprisPlrIface, "PlaybackStatus", mprisStatus(state))
}

func (b *mprisBackend) clearNowPlaying() {
	if b.props == nil {
		return
	}
	b.mu.Lock()
	b.state = domain.PlaybackStateStopped
	b.positionUs = 0
	b.mu.Unlock()
	b.props.SetMust(mprisPlrIface, "PlaybackStatus", "Stopped")
	b.props.SetMust(mprisPlrIface, "Metadata", map[string]dbus.Variant{})
}

func (b *mprisBackend) close() {
	if b.conn != nil {
		_, _ = b.conn.ReleaseName(mprisBusName)
		_ = b.conn.Close()
	}
}

func (b *mprisBackend) setActivateCallback(_ func()) {}

func (b *mprisBackend) dispatch(get func() func()) {
	b.mu.Lock()
	f := get()
	b.mu.Unlock()
	if f != nil {
		go f()
	}
}

func mprisStatus(state domain.PlaybackState) string {
	switch state {
	case domain.PlaybackStatePlaying:
		return "Playing"
	case domain.PlaybackStatePaused:
		return "Paused"
	default:
		return "Stopped"
	}
}

func trackObjectPath(id string) dbus.ObjectPath {
	var sb strings.Builder
	for _, r := range id {
		if (r >= 'A' && r <= 'Z') || (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '_' {
			sb.WriteRune(r)
		} else {
			sb.WriteRune('_')
		}
	}
	s := sb.String()
	if s == "" {
		s = "current"
	}
	return dbus.ObjectPath("/org/airmedy/track/" + s)
}

// --- D-Bus method handlers ---

type mprisRoot struct{ b *mprisBackend }

func (r *mprisRoot) Raise() *dbus.Error { return nil }
func (r *mprisRoot) Quit() *dbus.Error  { return nil }

type mprisPlayer struct{ b *mprisBackend }

func (m *mprisPlayer) Play() *dbus.Error {
	m.b.dispatch(func() func() { return m.b.play })
	return nil
}

func (m *mprisPlayer) Pause() *dbus.Error {
	m.b.dispatch(func() func() { return m.b.pause })
	return nil
}

func (m *mprisPlayer) Stop() *dbus.Error {
	m.b.dispatch(func() func() { return m.b.pause })
	return nil
}

func (m *mprisPlayer) PlayPause() *dbus.Error {
	m.b.mu.Lock()
	playing := m.b.state == domain.PlaybackStatePlaying
	f := m.b.play
	if playing {
		f = m.b.pause
	}
	m.b.mu.Unlock()
	if f != nil {
		go f()
	}
	return nil
}

func (m *mprisPlayer) Next() *dbus.Error {
	m.b.dispatch(func() func() { return m.b.next })
	return nil
}

func (m *mprisPlayer) Previous() *dbus.Error {
	m.b.dispatch(func() func() { return m.b.previous })
	return nil
}

// Seek moves by a relative offset in microseconds (may be negative).
// MPRIS exports this method by name over D-Bus, so the signature is fixed and
// cannot match io.Seeker; silence govet's stdmethods check.
//
//nolint:govet // MPRIS D-Bus method, signature dictated by the spec
func (m *mprisPlayer) Seek(offsetUs int64) *dbus.Error {
	m.b.mu.Lock()
	target := float64(m.b.positionUs+offsetUs) / 1e6
	seek := m.b.seek
	m.b.mu.Unlock()
	if target < 0 {
		target = 0
	}
	if seek != nil {
		go seek(target)
	}
	return nil
}

// SetPosition seeks to an absolute position in microseconds.
func (m *mprisPlayer) SetPosition(_ dbus.ObjectPath, posUs int64) *dbus.Error {
	m.b.mu.Lock()
	seek := m.b.seek
	m.b.mu.Unlock()
	if seek != nil {
		go seek(float64(posUs) / 1e6)
	}
	return nil
}

// OpenUri is part of the MPRIS Player interface; unsupported.
func (m *mprisPlayer) OpenUri(_ string) *dbus.Error { return nil }

// --- introspection ---

var mprisRootIntrospect = introspect.Interface{
	Name: mprisRootIface,
	Methods: []introspect.Method{
		{Name: "Raise"},
		{Name: "Quit"},
	},
	Properties: []introspect.Property{
		{Name: "CanQuit", Type: "b", Access: "read"},
		{Name: "CanRaise", Type: "b", Access: "read"},
		{Name: "HasTrackList", Type: "b", Access: "read"},
		{Name: "Identity", Type: "s", Access: "read"},
		{Name: "DesktopEntry", Type: "s", Access: "read"},
		{Name: "SupportedUriSchemes", Type: "as", Access: "read"},
		{Name: "SupportedMimeTypes", Type: "as", Access: "read"},
	},
}

var mprisPlayerIntrospect = introspect.Interface{
	Name: mprisPlrIface,
	Methods: []introspect.Method{
		{Name: "Play"},
		{Name: "Pause"},
		{Name: "PlayPause"},
		{Name: "Stop"},
		{Name: "Next"},
		{Name: "Previous"},
		{Name: "Seek", Args: []introspect.Arg{{Name: "Offset", Type: "x", Direction: "in"}}},
		{Name: "SetPosition", Args: []introspect.Arg{
			{Name: "TrackId", Type: "o", Direction: "in"},
			{Name: "Position", Type: "x", Direction: "in"},
		}},
		{Name: "OpenUri", Args: []introspect.Arg{{Name: "Uri", Type: "s", Direction: "in"}}},
	},
	Signals: []introspect.Signal{
		{Name: "Seeked", Args: []introspect.Arg{{Name: "Position", Type: "x"}}},
	},
	Properties: []introspect.Property{
		{Name: "PlaybackStatus", Type: "s", Access: "read"},
		{Name: "Metadata", Type: "a{sv}", Access: "read"},
		{Name: "Position", Type: "x", Access: "read"},
		{Name: "Volume", Type: "d", Access: "read"},
		{Name: "Rate", Type: "d", Access: "read"},
		{Name: "MinimumRate", Type: "d", Access: "read"},
		{Name: "MaximumRate", Type: "d", Access: "read"},
		{Name: "CanGoNext", Type: "b", Access: "read"},
		{Name: "CanGoPrevious", Type: "b", Access: "read"},
		{Name: "CanPlay", Type: "b", Access: "read"},
		{Name: "CanPause", Type: "b", Access: "read"},
		{Name: "CanSeek", Type: "b", Access: "read"},
		{Name: "CanControl", Type: "b", Access: "read"},
	},
}
