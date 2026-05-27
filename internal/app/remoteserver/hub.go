package remoteserver

import (
	"context"
	"encoding/json"
	"log/slog"
	"sync"
)

// Client represents a connected and authenticated WebSocket client.
type Client struct {
	send  chan []byte
	close func()
}

// Hub manages all authenticated WebSocket clients and fan-out broadcasting.
type Hub struct {
	mu         sync.RWMutex
	clients    map[*Client]bool
	broadcast  chan []byte
	register   chan *Client
	unregister chan *Client
	logger     *slog.Logger
}

func NewHub(logger *slog.Logger) *Hub {
	return &Hub{
		clients:    make(map[*Client]bool),
		broadcast:  make(chan []byte, 256),
		register:   make(chan *Client, 32),
		unregister: make(chan *Client, 32),
		logger:     logger,
	}
}

// Run processes register/unregister/broadcast events. Blocks until ctx is cancelled.
func (h *Hub) Run(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			h.mu.Lock()
			for c := range h.clients {
				c.close()
				delete(h.clients, c)
			}
			h.mu.Unlock()
			return
		case c := <-h.register:
			h.mu.Lock()
			h.clients[c] = true
			h.mu.Unlock()
		case c := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[c]; ok {
				delete(h.clients, c)
				c.close()
			}
			h.mu.Unlock()
		case msg := <-h.broadcast:
			h.fanOut(msg)
		}
	}
}

func (h *Hub) fanOut(msg []byte) {
	h.mu.RLock()
	clients := make([]*Client, 0, len(h.clients))
	for c := range h.clients {
		clients = append(clients, c)
	}
	h.mu.RUnlock()

	for _, c := range clients {
		select {
		case c.send <- msg:
		default:
			// Slow client — drop it
			h.unregister <- c
		}
	}
}

// BroadcastMessage serialises v to JSON and fans it out to all connected clients.
func (h *Hub) BroadcastMessage(v any) {
	data, err := json.Marshal(v)
	if err != nil {
		h.logger.Error("failed to marshal broadcast message", "error", err)
		return
	}
	select {
	case h.broadcast <- data:
	default:
		h.logger.Warn("broadcast channel full, dropping message")
	}
}
