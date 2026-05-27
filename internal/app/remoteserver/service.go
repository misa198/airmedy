package remoteserver

import (
	"context"
	"fmt"
	"log/slog"
	"math/rand"
	"net"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"
)

// Service manages the remote HTTP/WebSocket server lifecycle.
type Service struct {
	settingsSvc  *appsettings.SettingsService
	playerSvc    *player.PlayerService
	artworkCache domain.ArtworkCache
	remoteFS     RemoteFS
	logger       *slog.Logger

	mu         sync.Mutex
	hub        *Hub
	sessions   *SessionStore
	httpServer *http.Server
	hubCancel  context.CancelFunc
	commands   chan InboundMessage
	running    bool

	// listenerActive gates player listener callbacks when server is not running.
	listenerActive atomic.Bool
}

func NewService(
	settingsSvc *appsettings.SettingsService,
	playerSvc *player.PlayerService,
	artworkCache domain.ArtworkCache,
	remoteFS RemoteFS,
	logger *slog.Logger,
) *Service {
	s := &Service{
		settingsSvc:  settingsSvc,
		playerSvc:    playerSvc,
		artworkCache: artworkCache,
		remoteFS:     remoteFS,
		logger:       logger,
		commands:     make(chan InboundMessage, 64),
	}
	s.registerPlayerListeners()
	return s
}

func (s *Service) registerPlayerListeners() {
	s.playerSvc.AddStatusListener(func(status domain.PlayerStatus) {
		if !s.listenerActive.Load() {
			return
		}
		s.hub.BroadcastMessage(StatusMessage{Type: TypeStatus, Data: status})
	})
	s.playerSvc.AddQueueListener(func(queue []*domain.TrackDTO) {
		if !s.listenerActive.Load() {
			return
		}
		s.hub.BroadcastMessage(QueueMessage{Type: TypeQueue, Data: queue})
	})
	s.playerSvc.AddLyricsListener(func(lyric *domain.Lyric) {
		if !s.listenerActive.Load() {
			return
		}
		s.hub.BroadcastMessage(LyricsMessage{Type: TypeLyrics, Data: lyric})
	})
}

// OnStart is called by FX on application start.
func (s *Service) OnStart(ctx context.Context) error {
	settings, err := s.settingsSvc.GetSettings(ctx)
	if err != nil {
		return nil
	}
	if !settings.RemoteServerEnabled {
		return nil
	}
	return s.start(ctx, settings)
}

// OnStop is called by FX on application stop.
func (s *Service) OnStop(ctx context.Context) error {
	s.stop()
	return nil
}

// SetEnabled starts or stops the server and persists the setting.
func (s *Service) SetEnabled(ctx context.Context, enabled bool) error {
	settings, err := s.settingsSvc.GetSettings(ctx)
	if err != nil {
		return fmt.Errorf("failed to load settings: %w", err)
	}

	if enabled {
		if err := s.start(ctx, settings); err != nil {
			return err
		}
	} else {
		s.stop()
	}

	settings.RemoteServerEnabled = enabled
	return s.settingsSvc.SaveSettings(ctx, settings)
}

// RegeneratePassword creates a new 4-digit password, invalidates existing sessions, and saves.
func (s *Service) RegeneratePassword(ctx context.Context) (string, error) {
	password := fmt.Sprintf("%04d", rand.Intn(10000))
	settings, err := s.settingsSvc.GetSettings(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to load settings: %w", err)
	}
	settings.RemoteServerPassword = password
	if err := s.settingsSvc.SaveSettings(ctx, settings); err != nil {
		return "", err
	}
	s.mu.Lock()
	if s.sessions != nil {
		s.sessions.SetPassword(password)
	}
	s.mu.Unlock()
	return password, nil
}

// SetPassword validates and sets a user-provided 4-digit PIN.
func (s *Service) SetPassword(ctx context.Context, password string) error {
	if len(password) != 4 {
		return fmt.Errorf("password must be 4 digits")
	}
	for _, c := range password {
		if c < '0' || c > '9' {
			return fmt.Errorf("password must be numeric")
		}
	}
	settings, err := s.settingsSvc.GetSettings(ctx)
	if err != nil {
		return fmt.Errorf("failed to load settings: %w", err)
	}
	settings.RemoteServerPassword = password
	if err := s.settingsSvc.SaveSettings(ctx, settings); err != nil {
		return err
	}
	s.mu.Lock()
	if s.sessions != nil {
		s.sessions.SetPassword(password)
	}
	s.mu.Unlock()
	return nil
}

// GetSettings returns the current app settings (convenience for the Wails bridge).
func (s *Service) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	return s.settingsSvc.GetSettings(ctx)
}

// GetPort returns the currently bound port (0 if not running).
func (s *Service) GetPort() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.running || s.httpServer == nil {
		return 0
	}
	addr := s.httpServer.Addr
	_, portStr, err := net.SplitHostPort(addr)
	if err != nil {
		return 0
	}
	port := 0
	fmt.Sscanf(portStr, "%d", &port)
	return port
}

// IsRunning reports whether the server is active.
func (s *Service) IsRunning() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.running
}

func (s *Service) start(ctx context.Context, settings *domain.AppSettings) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.running {
		return nil
	}

	// Generate password if not set
	if settings.RemoteServerPassword == "" {
		settings.RemoteServerPassword = fmt.Sprintf("%04d", rand.Intn(10000))
		_ = s.settingsSvc.SaveSettings(ctx, settings)
	}

	// Select port
	port, err := selectPort(settings.RemoteServerPort)
	if err != nil {
		return fmt.Errorf("no available port: %w", err)
	}

	// Save port if it changed
	if port != settings.RemoteServerPort {
		settings.RemoteServerPort = port
		_ = s.settingsSvc.SaveSettings(ctx, settings)
	}

	hubCtx, hubCancel := context.WithCancel(context.Background())
	hub := NewHub(s.logger)
	sessions := NewSessionStore(settings.RemoteServerPassword)

	handler := newHandler(hub, sessions, s.playerSvc, s.settingsSvc, s.artworkCache, s.remoteFS, s.commands)

	srv := &http.Server{
		Addr:         fmt.Sprintf(":%d", port),
		Handler:      handler,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	go hub.Run(hubCtx)
	go s.processCommands()

	go func() {
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			s.logger.Error("remote server error", "error", err)
		}
	}()

	s.hub = hub
	s.sessions = sessions
	s.httpServer = srv
	s.hubCancel = hubCancel
	s.running = true
	s.listenerActive.Store(true)

	s.logger.Info("remote server started", "port", port)
	return nil
}

func (s *Service) stop() {
	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.running {
		return
	}

	s.listenerActive.Store(false)
	s.hubCancel()

	stopCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = s.httpServer.Shutdown(stopCtx)

	s.running = false
	s.httpServer = nil
	s.hub = nil
	s.sessions = nil
	s.hubCancel = nil

	s.logger.Info("remote server stopped")
}

func (s *Service) processCommands() {
	for msg := range s.commands {
		if !s.listenerActive.Load() {
			continue
		}
		s.handleCommand(msg)
	}
}

func (s *Service) handleCommand(msg InboundMessage) {
	p := s.playerSvc
	switch msg.Type {
	case TypePlay:
		_ = p.Play()
	case TypePause:
		_ = p.Pause()
	case TypeTogglePause:
		_ = p.TogglePause()
	case TypeNext:
		_ = p.Next()
	case TypePrev:
		_ = p.Previous()
	case TypeSeek:
		_ = p.Seek(msg.Position)
	case TypeSetVolume:
		_ = p.SetVolume(msg.Volume)
	case TypeSetMuted:
		_ = p.SetMuted(msg.Muted)
	case TypeSetShuffle:
		_ = p.SetShuffle(msg.Enabled)
	case TypeSetRepeat:
		_ = p.SetRepeatMode(domain.RepeatMode(msg.Mode))
	case TypePlayQueueIndex:
		_ = p.PlayQueueIndex(msg.Index)
	case TypeRemoveFromQueue:
		p.RemoveFromQueue(msg.TrackID)
	case TypeReorderQueue:
		p.ReorderQueue(msg.TrackIDs)
	}
}

// selectPort tries the cached port first, then picks a random one in 49152–65535.
func selectPort(cached int) (int, error) {
	if cached > 0 {
		if isPortAvailable(cached) {
			return cached, nil
		}
	}
	for i := 0; i < 100; i++ {
		port := 49152 + rand.Intn(65535-49152)
		if isPortAvailable(port) {
			return port, nil
		}
	}
	return 0, fmt.Errorf("could not find an available port in range 49152-65535")
}

func isPortAvailable(port int) bool {
	ln, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		return false
	}
	_ = ln.Close()
	return true
}
