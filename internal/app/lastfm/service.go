package lastfm

import (
	"context"
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"sort"
	"strings"
	"sync"
	"time"

	"airmedy/internal/app/appsettings"
	"airmedy/internal/app/config"
	"airmedy/internal/app/player"
	"airmedy/internal/domain"

	"github.com/pkg/browser"
	"github.com/wailsapp/wails/v3/pkg/application"
	"github.com/zalando/go-keyring"
)

const (
	apiBase     = "https://ws.audioscrobbler.com/2.0/"
	serviceName = "airmedy"
	keyringKey  = "lastfm_session_key"
)

type LastFmService struct {
	mu         sync.RWMutex
	settings   *appsettings.SettingsService
	player     *player.PlayerService
	logger     *slog.Logger
	apiKey     string
	apiSecret  string
	sessionKey string
	username   string
	avatarURL  string
}

func NewLastFmService(
	settings *appsettings.SettingsService,
	player *player.PlayerService,
	logger *slog.Logger,
) *LastFmService {
	s := &LastFmService{
		settings:  settings,
		player:    player,
		logger:    logger,
		apiKey:    config.LastFmAPIKey,
		apiSecret: config.LastFmAPISecret,
	}

	// Load session from keyring
	if key, err := keyring.Get(serviceName, keyringKey); err == nil {
		s.sessionKey = key
	}

	// Load username from settings
	if st, err := settings.GetSettings(context.Background()); err == nil {
		s.username = st.LastFmUsername
	}

	// Fetch avatar if connected
	if s.sessionKey != "" && s.username != "" {
		go s.fetchUserAvatar(s.username)
	}

	player.AddScrobbleListener(s.handleScrobble)
	player.AddNowPlayingListener(s.handleNowPlaying)

	return s
}

func (s *LastFmService) Connect(ctx context.Context) error {
	if s.apiKey == "" || s.apiSecret == "" {
		return fmt.Errorf("last.fm API keys not configured")
	}
	cbURL := "airmedy://auth"
	authURL := fmt.Sprintf("https://www.last.fm/api/auth/?api_key=%s&cb=%s",
		s.apiKey, url.QueryEscape(cbURL))

	s.logger.Debug("starting lastfm auth flow with deep link", "callback_url", cbURL, "auth_url", authURL)

	if err := browser.OpenURL(authURL); err != nil {
		return fmt.Errorf("failed to open browser: %w", err)
	}

	return nil
}

func (s *LastFmService) CompleteAuth(ctx context.Context, token string) error {
	s.logger.Debug("completing lastfm auth", "token", token)

	session, username, err := s.getSession(token)
	if err != nil {
		s.logger.Error("lastfm session fetch failed", "error", err)
		return err
	}

	s.mu.Lock()
	s.sessionKey = session
	s.username = username
	s.mu.Unlock()

	// Save session key to keyring
	if err := keyring.Set(serviceName, keyringKey, session); err != nil {
		s.logger.Error("failed to save session to keyring", "error", err)
	}

	// Save username to settings
	st, err := s.settings.GetSettings(ctx)
	if err != nil {
		return err
	}
	st.LastFmUsername = username
	if err := s.settings.SaveSettings(ctx, st); err != nil {
		return err
	}

	s.logger.Info("lastfm authentication successful via deep link", "username", username)

	// Fetch avatar in background
	go s.fetchUserAvatar(username)

	// Emit event to frontend
	app := application.Get()
	if app != nil && app.Event != nil {
		app.Event.Emit("lastfm:connected", username)
	}

	return nil
}

func (s *LastFmService) Disconnect(ctx context.Context) error {
	s.mu.Lock()
	s.sessionKey = ""
	s.username = ""
	s.avatarURL = ""
	s.mu.Unlock()

	_ = keyring.Delete(serviceName, keyringKey)

	st, _ := s.settings.GetSettings(ctx)
	st.LastFmUsername = ""
	return s.settings.SaveSettings(ctx, st)
}

func (s *LastFmService) GetStatus() (bool, string, string) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.sessionKey != "", s.username, s.avatarURL
}

// Internal API helpers

func (s *LastFmService) getSession(token string) (string, string, error) {
	params := map[string]string{
		"method":  "auth.getSession",
		"api_key": s.apiKey,
		"token":   token,
		"format":  "json",
	}
	params["api_sig"] = s.generateSignature(params)

	resp, err := http.Get(apiBase + "?" + s.encodeParams(params))
	if err != nil {
		return "", "", err
	}
	defer func() { _ = resp.Body.Close() }()

	var result struct {
		Session struct {
			Name string `json:"name"`
			Key  string `json:"key"`
		} `json:"session"`
		Error   int    `json:"error"`
		Message string `json:"message"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", "", err
	}

	if result.Error != 0 {
		return "", "", fmt.Errorf("lastfm error %d: %s", result.Error, result.Message)
	}

	return result.Session.Key, result.Session.Name, nil
}

func (s *LastFmService) fetchUserAvatar(username string) {
	params := map[string]string{
		"method":  "user.getInfo",
		"user":    username,
		"api_key": s.apiKey,
		"format":  "json",
	}

	resp, err := http.Get(apiBase + "?" + s.encodeParams(params))
	if err != nil {
		s.logger.Warn("failed to fetch lastfm user info", "error", err)
		return
	}
	defer func() { _ = resp.Body.Close() }()

	var result struct {
		User struct {
			Image []struct {
				Text string `json:"#text"`
				Size string `json:"size"`
			} `json:"image"`
		} `json:"user"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		s.logger.Warn("failed to decode lastfm user info", "error", err)
		return
	}

	// Get largest image
	avatarURL := ""
	if len(result.User.Image) > 0 {
		avatarURL = result.User.Image[len(result.User.Image)-1].Text
	}

	if avatarURL != "" {
		s.mu.Lock()
		s.avatarURL = avatarURL
		s.mu.Unlock()

		s.logger.Debug("fetched lastfm user avatar", "url", avatarURL)
		app := application.Get()
		if app != nil && app.Event != nil {
			app.Event.Emit("lastfm:avatar", avatarURL)
		}
	}
}

func (s *LastFmService) updateNowPlaying(track *domain.TrackDTO) {
	s.mu.RLock()
	sk := s.sessionKey
	s.mu.RUnlock()
	if sk == "" {
		return
	}

	artist := ""
	if len(track.Artists) > 0 {
		artist = track.Artists[0].Name
	}

	params := map[string]string{
		"method":  "track.updateNowPlaying",
		"api_key": s.apiKey,
		"sk":      sk,
		"artist":  artist,
		"track":   track.Title,
		"format":  "json",
	}

	if track.Album != nil && track.Album.Title != "" && track.Album.Title != "Unknown Album" {
		params["album"] = track.Album.Title
	}
	if len(track.AlbumArtists) > 0 {
		params["albumArtist"] = track.AlbumArtists[0].Name
	}
	if track.TrackNumber > 0 {
		params["trackNumber"] = fmt.Sprintf("%d", track.TrackNumber)
	}
	if track.Duration > 0 {
		params["duration"] = fmt.Sprintf("%d", track.Duration)
	}

	params["api_sig"] = s.generateSignature(params)

	go s.postParams(params)
}

func (s *LastFmService) scrobble(track *domain.TrackDTO, timestamp int64) {
	s.mu.RLock()
	sk := s.sessionKey
	s.mu.RUnlock()
	if sk == "" {
		return
	}

	artist := ""
	if len(track.Artists) > 0 {
		artist = track.Artists[0].Name
	}

	params := map[string]string{
		"method":    "track.scrobble",
		"api_key":   s.apiKey,
		"sk":        sk,
		"artist":    artist,
		"track":     track.Title,
		"timestamp": fmt.Sprintf("%d", timestamp),
		"format":    "json",
	}

	if track.Album != nil && track.Album.Title != "" && track.Album.Title != "Unknown Album" {
		params["album"] = track.Album.Title
	}
	if len(track.AlbumArtists) > 0 {
		params["albumArtist"] = track.AlbumArtists[0].Name
	}
	if track.TrackNumber > 0 {
		params["trackNumber"] = fmt.Sprintf("%d", track.TrackNumber)
	}
	if track.Duration > 0 {
		params["duration"] = fmt.Sprintf("%d", track.Duration)
	}

	params["api_sig"] = s.generateSignature(params)

	go s.postParams(params)
}

func (s *LastFmService) SetLoveStatus(track *domain.TrackDTO, loved bool) {
	s.mu.RLock()
	sk := s.sessionKey
	s.mu.RUnlock()
	if sk == "" {
		return
	}

	artist := ""
	if len(track.Artists) > 0 {
		artist = track.Artists[0].Name
	}

	method := "track.love"
	if !loved {
		method = "track.unlove"
	}

	params := map[string]string{
		"method":  method,
		"api_key": s.apiKey,
		"sk":      sk,
		"artist":  artist,
		"track":   track.Title,
		"format":  "json",
	}

	params["api_sig"] = s.generateSignature(params)

	go s.postParams(params)
}

func (s *LastFmService) generateSignature(params map[string]string) string {
	keys := make([]string, 0, len(params))
	for k := range params {
		if k == "format" || k == "callback" || k == "api_sig" {
			continue
		}
		keys = append(keys, k)
	}
	sort.Strings(keys)

	var b strings.Builder
	for _, k := range keys {
		b.WriteString(k)
		b.WriteString(params[k])
	}
	b.WriteString(s.apiSecret)

	baseString := b.String()
	hash := md5.Sum([]byte(baseString))
	sig := hex.EncodeToString(hash[:])

	s.logger.Debug("generated lastfm signature", "base_string", baseString, "sig", sig)
	return sig
}

func (s *LastFmService) encodeParams(params map[string]string) string {
	v := url.Values{}
	for k, val := range params {
		v.Add(k, val)
	}
	return v.Encode()
}

func (s *LastFmService) postParams(params map[string]string) {
	resp, err := http.PostForm(apiBase, s.toUrlValues(params))
	if err != nil {
		s.logger.Warn("lastfm post failed", "error", err)
		return
	}
	defer func() { _ = resp.Body.Close() }()
	body, _ := io.ReadAll(resp.Body)
	s.logger.Debug("lastfm api response", "method", params["method"], "status", resp.Status, "body", string(body))
}

func (s *LastFmService) toUrlValues(params map[string]string) url.Values {
	v := url.Values{}
	for k, val := range params {
		v.Add(k, val)
	}
	return v
}

func (s *LastFmService) handleScrobble(track *domain.TrackDTO, startTime time.Time) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.sessionKey == "" {
		return
	}

	go s.scrobble(track, startTime.Unix())
	s.logger.Debug("lastfm scrobble submitted", "title", track.Title)
}

func (s *LastFmService) handleNowPlaying(track *domain.TrackDTO) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.sessionKey == "" {
		return
	}

	go s.updateNowPlaying(track)
	s.logger.Debug("lastfm now playing updated", "title", track.Title)
}
