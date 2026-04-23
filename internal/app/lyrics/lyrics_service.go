package lyrics

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"changeme/internal/domain"
)

type LyricsService struct {
	repo   domain.LyricRepository
	logger *slog.Logger
	client *http.Client
}

func NewLyricsService(repo domain.LyricRepository, logger *slog.Logger) *LyricsService {
	return &LyricsService{
		repo:   repo,
		logger: logger,
		client: &http.Client{Timeout: 10 * time.Second},
	}
}

func (s *LyricsService) GetLyrics(ctx context.Context, trackID string) (*domain.Lyric, error) {
	return s.repo.GetByTrackID(ctx, trackID)
}

func (s *LyricsService) SaveLyrics(ctx context.Context, trackID, content, source string) error {
	return s.repo.Upsert(ctx, &domain.Lyric{
		TrackID: trackID,
		Content: content,
		Source:  source,
	})
}

func (s *LyricsService) DeleteLyrics(ctx context.Context, trackID string) error {
	return s.repo.Delete(ctx, trackID)
}

// FetchFromExternal tries lrclib.net for the given track and saves the result.
// Returns the saved lyric, or nil if not found.
func (s *LyricsService) FetchFromExternal(ctx context.Context, track *domain.TrackDTO) (*domain.Lyric, error) {
	artistName := track.RawArtistNames
	if len(track.Artists) > 0 && track.Artists[0] != nil {
		artistName = track.Artists[0].Name
	}
	albumName := ""
	if track.Album != nil {
		albumName = track.Album.Title
	}

	params := url.Values{}
	params.Set("track_name", track.Title)
	params.Set("artist_name", artistName)
	if albumName != "" {
		params.Set("album_name", albumName)
	}
	if track.Duration > 0 {
		params.Set("duration", strconv.Itoa(track.Duration))
	}

	reqURL := "https://lrclib.net/api/get?" + params.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to build lrclib request: %w", err)
	}
	req.Header.Set("User-Agent", "Airmedy/1.0")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("lrclib request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, nil
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("lrclib returned status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read lrclib response: %w", err)
	}

	var result struct {
		SyncedLyrics string `json:"syncedLyrics"`
		PlainLyrics  string `json:"plainLyrics"`
	}
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("failed to parse lrclib response: %w", err)
	}

	content := result.SyncedLyrics
	source := "lrclib-synced"
	if strings.TrimSpace(content) == "" {
		content = result.PlainLyrics
		source = "lrclib-plain"
	}
	if strings.TrimSpace(content) == "" {
		return nil, nil
	}

	lyric := &domain.Lyric{
		TrackID: track.ID,
		Content: content,
		Source:  source,
	}
	if err := s.repo.Upsert(ctx, lyric); err != nil {
		s.logger.Warn("Failed to save fetched lyrics", "track_id", track.ID, "error", err)
	}
	return lyric, nil
}
