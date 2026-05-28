package remoteserver

import (
	"context"
	"encoding/json"
	"io/fs"
	"net/http"
	"os"
	"strings"
	"time"

	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/coder/websocket"
	"github.com/coder/websocket/wsjson"
)

// Handler serves the remote HTTP API and WebSocket endpoint.
type Handler struct {
	hub          *Hub
	sessions     *SessionStore
	playerSvc    *player.PlayerService
	settingsSvc  *appsettings.SettingsService
	artworkCache domain.ArtworkCache
	remoteFS     RemoteFS
	commands     chan<- InboundMessage
}

func newHandler(
	hub *Hub,
	sessions *SessionStore,
	playerSvc *player.PlayerService,
	settingsSvc *appsettings.SettingsService,
	artworkCache domain.ArtworkCache,
	remoteFS RemoteFS,
	commands chan<- InboundMessage,
) *Handler {
	return &Handler{
		hub:          hub,
		sessions:     sessions,
		playerSvc:    playerSvc,
		settingsSvc:  settingsSvc,
		artworkCache: artworkCache,
		remoteFS:     remoteFS,
		commands:     commands,
	}
}

func (h *Handler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	switch {
	case r.URL.Path == "/ws":
		h.handleWS(w, r)
	case r.URL.Path == "/api/settings":
		h.handleSettings(w, r)
	case strings.HasPrefix(r.URL.Path, "/artwork/"):
		h.handleArtwork(w, r)
	default:
		h.handleStatic(w, r)
	}
}

func (h *Handler) handleSettings(w http.ResponseWriter, r *http.Request) {
	settings, err := h.settingsSvc.GetSettings(r.Context())
	if err != nil {
		http.Error(w, "failed to get settings", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(map[string]string{
		"language": settings.Language,
	}); err != nil {
		http.Error(w, "failed to encode response", http.StatusInternalServerError)
	}
}

func (h *Handler) handleArtwork(w http.ResponseWriter, r *http.Request) {
	key := strings.TrimPrefix(r.URL.Path, "/artwork/")
	if key == "" {
		http.NotFound(w, r)
		return
	}
	size := r.URL.Query().Get("size")
	if size == "sm" || size == "md" {
		variantPath := h.artworkCache.GetVariantPath(key, size)
		if _, err := os.Stat(variantPath); err == nil {
			http.ServeFile(w, r, variantPath)
			return
		}
	}
	filePath := h.artworkCache.GetPath(key)
	if _, err := os.Stat(filePath); os.IsNotExist(err) {
		http.NotFound(w, r)
		return
	}
	http.ServeFile(w, r, filePath)
}

func (h *Handler) handleStatic(w http.ResponseWriter, r *http.Request) {
	fileServer := http.FileServer(http.FS(h.remoteFS))
	path := strings.TrimPrefix(r.URL.Path, "/")
	if path == "" {
		path = "index.html"
	}
	if _, err := fs.Stat(h.remoteFS, path); err != nil {
		// SPA fallback
		r = r.Clone(r.Context())
		r.URL.Path = "/"
	}
	fileServer.ServeHTTP(w, r)
}

func (h *Handler) handleWS(w http.ResponseWriter, r *http.Request) {
	conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
		OriginPatterns: []string{"*"},
	})
	if err != nil {
		return
	}

	ctx, cancel := context.WithCancel(r.Context())
	defer cancel()
	defer func() { _ = conn.Close(websocket.StatusNormalClosure, "") }()

	client := &Client{
		send: make(chan []byte, 256),
	}
	client.close = func() {
		cancel()
		close(client.send)
	}

	// Write pump
	go func() {
		for {
			select {
			case <-ctx.Done():
				return
			case msg, ok := <-client.send:
				if !ok {
					return
				}
				writeCtx, writeCancel := context.WithTimeout(ctx, 10*time.Second)
				writeErr := conn.Write(writeCtx, websocket.MessageText, msg)
				writeCancel()
				if writeErr != nil {
					cancel()
					return
				}
			}
		}
	}()

	// Step 1: send auth_required
	authReqMsg, _ := json.Marshal(AuthRequired{Type: TypeAuthRequired})
	client.send <- authReqMsg

	// Step 2: read auth message — long timeout so user has time to read and type PIN
	authCtx, authCancel := context.WithTimeout(ctx, 5*time.Minute)
	defer authCancel()

	var firstMsg InboundMessage
	if err := wsjson.Read(authCtx, conn, &firstMsg); err != nil {
		return
	}

	if firstMsg.Type != TypeAuth {
		errData, _ := json.Marshal(ErrorMessage{Type: TypeError, Message: "auth required"})
		client.send <- errData
		time.Sleep(100 * time.Millisecond)
		return
	}

	// Step 3: validate credentials
	var token string
	if firstMsg.Token != "" && h.sessions.Validate(firstMsg.Token) {
		token = firstMsg.Token
	} else if firstMsg.Password != "" {
		var ok bool
		token, ok = h.sessions.Authenticate(firstMsg.Password)
		if !ok {
			failData, _ := json.Marshal(AuthFailed{Type: TypeAuthFailed, Reason: "invalid_password"})
			client.send <- failData
			time.Sleep(100 * time.Millisecond)
			return
		}
	} else {
		failData, _ := json.Marshal(AuthFailed{Type: TypeAuthFailed, Reason: "no_credentials"})
		client.send <- failData
		time.Sleep(100 * time.Millisecond)
		return
	}

	// Step 4: send auth_ok with full state
	authOk := AuthOk{
		Type:  TypeAuthOk,
		Token: token,
		State: AuthOkState{
			TrackMetadata: h.playerSvc.GetTrackMetadata(),
			PlayerState:   h.playerSvc.GetRemotePlayerState(),
			Queue:         h.playerSvc.GetQueue(),
		},
	}
	authOkData, _ := json.Marshal(authOk)
	client.send <- authOkData

	// Step 5: register with hub (now authenticated)
	h.hub.register <- client
	defer func() {
		h.hub.unregister <- client
	}()

	// Step 6: main read loop
	for {
		var msg InboundMessage
		if err := wsjson.Read(ctx, conn, &msg); err != nil {
			return
		}
		select {
		case h.commands <- msg:
		default:
		}
	}
}
