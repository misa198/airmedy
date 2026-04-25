package lyrics

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"airmedy/internal/domain"
)

var noisePatterns = []*regexp.Regexp{
	regexp.MustCompile(`(?i)\s*\(feat\.?[^)]*\)`),
	regexp.MustCompile(`(?i)\s*\[feat\.?[^]]*\]`),
	regexp.MustCompile(`(?i)\s*\(ft\.?[^)]*\)`),
	regexp.MustCompile(`(?i)\s*\((official\s*(video|audio|lyric.*?|music video)|lyrics?|hd|4k|remaster.*?)\)`),
	regexp.MustCompile(`(?i)\s*\[(official\s*(video|audio|lyric.*?|music video)|lyrics?|hd|4k|remaster.*?)\]`),
}

var featuredRe = regexp.MustCompile(`(?i)\s*[\(\[]fe?a?t\.?\s*([^\)\]]+)[\)\]]`)

const (
	titleWeight     = 0.5
	artistWeight    = 0.3
	durationWeight  = 0.2
	maxDurationDiff = 5.0
	minTitleSim     = 0.7
)

type lrclibCandidate struct {
	TrackName    string  `json:"trackName"`
	ArtistName   string  `json:"artistName"`
	Duration     float64 `json:"duration"`
	SyncedLyrics string  `json:"syncedLyrics"`
	PlainLyrics  string  `json:"plainLyrics"`
}

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
	artistRaw := track.RawArtistNames
	if len(track.Artists) > 0 && track.Artists[0] != nil {
		artistRaw = track.Artists[0].Name
	}
	albumName := ""
	if track.Album != nil {
		albumName = track.Album.Title
	}

	cleanTitle, _ := extractFeatured(track.Title)
	normTitle := normalizeText(cleanTitle)
	normArtist := normalizeText(artistRaw)

	// Attempt exact match first.
	lyric, err := s.exactGet(ctx, track.ID, normTitle, normArtist, albumName, track.Duration)
	if err != nil {
		return nil, err
	}
	if lyric != nil {
		return lyric, nil
	}

	// Retry without album name if it was included (album mismatch causes 404 on lrclib).
	if albumName != "" {
		lyric, err = s.exactGet(ctx, track.ID, normTitle, normArtist, "", track.Duration)
		if err != nil {
			return nil, err
		}
		if lyric != nil {
			return lyric, nil
		}
	}

	// Fallback: search and rank candidates.
	return s.searchAndRank(ctx, track.ID, normTitle, normArtist, track.Duration)
}

func (s *LyricsService) exactGet(ctx context.Context, trackID, title, artist, album string, duration int) (*domain.Lyric, error) {
	params := url.Values{}
	params.Set("track_name", title)
	params.Set("artist_name", artist)
	if album != "" {
		params.Set("album_name", album)
	}
	if duration > 0 {
		params.Set("duration", strconv.Itoa(duration))
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://lrclib.net/api/get?"+params.Encode(), nil)
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

	var result lrclibCandidate
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("failed to parse lrclib response: %w", err)
	}

	content, source := pickContent(result.SyncedLyrics, result.PlainLyrics)
	if content == "" {
		return nil, nil
	}
	return s.saveLyric(ctx, trackID, content, source)
}

func (s *LyricsService) searchAndRank(ctx context.Context, trackID, normTitle, normArtist string, duration int) (*domain.Lyric, error) {
	params := url.Values{}
	params.Set("track_name", normTitle)
	params.Set("artist_name", normArtist)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://lrclib.net/api/search?"+params.Encode(), nil)
	if err != nil {
		return nil, fmt.Errorf("failed to build lrclib search request: %w", err)
	}
	req.Header.Set("User-Agent", "Airmedy/1.0")

	resp, err := s.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("lrclib search request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusNotFound {
		return nil, nil
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("lrclib search returned status %d", resp.StatusCode)
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read lrclib search response: %w", err)
	}

	var candidates []lrclibCandidate
	if err := json.Unmarshal(body, &candidates); err != nil {
		return nil, fmt.Errorf("failed to parse lrclib search response: %w", err)
	}

	best := -1
	bestScore := -1.0
	for i, c := range candidates {
		score := scoreCandidate(c, normTitle, normArtist, duration)
		if score > bestScore {
			bestScore = score
			best = i
		}
	}

	if best < 0 {
		return nil, nil
	}

	c := candidates[best]
	content, source := pickContent(c.SyncedLyrics, c.PlainLyrics)
	if content == "" {
		return nil, nil
	}
	return s.saveLyric(ctx, trackID, content, source)
}

func (s *LyricsService) saveLyric(ctx context.Context, trackID, content, source string) (*domain.Lyric, error) {
	lyric := &domain.Lyric{TrackID: trackID, Content: content, Source: source}
	if err := s.repo.Upsert(ctx, lyric); err != nil {
		s.logger.Warn("failed to save fetched lyrics", "track_id", trackID, "error", err)
	}
	return lyric, nil
}

func normalizeText(s string) string {
	for _, re := range noisePatterns {
		s = re.ReplaceAllString(s, "")
	}
	s = strings.ToLower(strings.TrimSpace(s))
	return strings.Join(strings.Fields(s), " ")
}

func extractFeatured(title string) (cleanTitle, featured string) {
	m := featuredRe.FindStringSubmatch(title)
	if m == nil {
		return title, ""
	}
	return strings.TrimSpace(featuredRe.ReplaceAllString(title, "")), strings.TrimSpace(m[1])
}

func pickContent(synced, plain string) (content, source string) {
	if strings.TrimSpace(synced) != "" {
		return synced, "lrclib-synced"
	}
	if strings.TrimSpace(plain) != "" {
		return plain, "lrclib-plain"
	}
	return "", ""
}

func scoreCandidate(c lrclibCandidate, wantTitle, wantArtist string, wantDuration int) float64 {
	titleSim := similarity(normalizeText(c.TrackName), wantTitle)
	if titleSim < minTitleSim {
		return -1
	}
	durDiff := math.Abs(c.Duration - float64(wantDuration))
	if durDiff > maxDurationDiff {
		return -1
	}
	artistSim := similarity(normalizeText(c.ArtistName), wantArtist)
	durScore := 1.0 - (durDiff / maxDurationDiff)
	return titleSim*titleWeight + artistSim*artistWeight + durScore*durationWeight
}

func similarity(a, b string) float64 {
	if a == b {
		return 1.0
	}
	if len(a) == 0 || len(b) == 0 {
		return 0.0
	}
	ra, rb := []rune(a), []rune(b)
	la, lb := len(ra), len(rb)
	prev := make([]int, lb+1)
	for j := range prev {
		prev[j] = j
	}
	for i := 1; i <= la; i++ {
		curr := make([]int, lb+1)
		curr[0] = i
		for j := 1; j <= lb; j++ {
			if ra[i-1] == rb[j-1] {
				curr[j] = prev[j-1]
			} else {
				curr[j] = 1 + min3(prev[j], curr[j-1], prev[j-1])
			}
		}
		prev = curr
	}
	return 1.0 - float64(prev[lb])/float64(max(la, lb))
}

func min3(a, b, c int) int {
	if a < b {
		if a < c {
			return a
		}
		return c
	}
	if b < c {
		return b
	}
	return c
}
