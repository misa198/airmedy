package remoteserver

import (
	"crypto/subtle"
	"sync"
	"time"

	"github.com/google/uuid"
)

type Session struct {
	Token     string
	CreatedAt time.Time
	LastSeen  time.Time
}

type SessionStore struct {
	mu       sync.RWMutex
	sessions map[string]*Session
	password string
}

func NewSessionStore(password string) *SessionStore {
	return &SessionStore{
		sessions: make(map[string]*Session),
		password: password,
	}
}

// Authenticate validates a password and returns a new session token on success.
func (s *SessionStore) Authenticate(password string) (string, bool) {
	s.mu.RLock()
	pw := s.password
	s.mu.RUnlock()
	if subtle.ConstantTimeCompare([]byte(password), []byte(pw)) != 1 {
		return "", false
	}
	token := uuid.New().String()
	now := time.Now()
	s.mu.Lock()
	s.sessions[token] = &Session{Token: token, CreatedAt: now, LastSeen: now}
	s.mu.Unlock()
	return token, true
}

// Validate checks a token and updates its LastSeen on success.
func (s *SessionStore) Validate(token string) bool {
	if token == "" {
		return false
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	sess, ok := s.sessions[token]
	if !ok {
		return false
	}
	sess.LastSeen = time.Now()
	return true
}

// SetPassword updates the password and invalidates all existing sessions.
func (s *SessionStore) SetPassword(password string) {
	s.mu.Lock()
	s.password = password
	s.sessions = make(map[string]*Session)
	s.mu.Unlock()
}
