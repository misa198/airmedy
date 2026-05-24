package i18n

import (
	"embed"
	"encoding/json"
	"fmt"
	"log/slog"
	"path/filepath"
	"strings"
	"sync"
)

//go:embed locales/*.json
var localesFS embed.FS

type Service struct {
	mu           sync.RWMutex
	translations map[string]map[string]interface{}
	current      string
	fallback     string
	logger       *slog.Logger
}

func NewService(logger *slog.Logger) *Service {
	s := &Service{
		translations: make(map[string]map[string]interface{}),
		current:      "en",
		fallback:     "en",
		logger:       logger,
	}

	if err := s.loadLocales(); err != nil {
		logger.Error("failed to load backend locales", "error", err)
	}

	return s
}

func (s *Service) loadLocales() error {
	entries, err := localesFS.ReadDir("locales")
	if err != nil {
		return err
	}

	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".json") {
			continue
		}

		lang := strings.TrimSuffix(entry.Name(), ".json")
		data, err := localesFS.ReadFile(filepath.Join("locales", entry.Name()))
		if err != nil {
			s.logger.Error("failed to read locale file", "file", entry.Name(), "error", err)
			continue
		}

		var m map[string]interface{}
		if err := json.Unmarshal(data, &m); err != nil {
			s.logger.Error("failed to parse locale file", "file", entry.Name(), "error", err)
			continue
		}

		s.translations[lang] = m
	}

	return nil
}

func (s *Service) SetLocale(lang string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.current = lang
}

func (s *Service) GetLocale() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.current
}

func (s *Service) T(key string, args ...any) string {
	s.mu.RLock()
	defer s.mu.RUnlock()

	parts := strings.Split(key, ".")
	val := s.lookup(s.current, parts)
	if val == "" && s.current != s.fallback {
		val = s.lookup(s.fallback, parts)
	}
	if val == "" {
		return key
	}
	if len(args) > 0 {
		return fmt.Sprintf(val, args...)
	}
	return val
}

func (s *Service) lookup(lang string, parts []string) string {
	m, ok := s.translations[lang]
	if !ok {
		return ""
	}

	var current interface{} = m
	for _, part := range parts {
		if cmap, ok := current.(map[string]interface{}); ok {
			current = cmap[part]
		} else {
			return ""
		}
	}

	if str, ok := current.(string); ok {
		return str
	}
	return ""
}
