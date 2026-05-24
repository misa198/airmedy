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
	"strconv"
	"strings"
	"time"

	"airmedy/internal/domain"
)

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

type LrclibProvider struct {
	client *http.Client
	logger *slog.Logger
}

func NewLrclibProvider(logger *slog.Logger) *LrclibProvider {
	return &LrclibProvider{
		client: &http.Client{Timeout: 30 * time.Second},
		logger: logger,
	}
}

func (p *LrclibProvider) Name() string { return "lrclib" }

func (p *LrclibProvider) Search(ctx context.Context, title, artist string, duration int) ([]*domain.LyricsSearchResult, error) {
	p.logger.Debug("lrclib: searching lyrics", "title", title, "artist", artist, "duration", duration)
	params := url.Values{}
	params.Set("track_name", title)
	params.Set("artist_name", artist)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://lrclib.net/api/search?"+params.Encode(), nil)
	if err != nil {
		return nil, fmt.Errorf("failed to build lrclib search request: %w", err)
	}
	req.Header.Set("User-Agent", "Airmedy/1.0")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("lrclib search request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode == http.StatusNotFound {
		p.logger.Debug("lrclib: search results not found")
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

	p.logger.Debug("lrclib: search found candidates", "count", len(candidates))

	var results []*domain.LyricsSearchResult
	for _, c := range candidates {
		content, source := pickContent(c.SyncedLyrics, c.PlainLyrics)
		if content == "" {
			continue
		}
		results = append(results, &domain.LyricsSearchResult{
			Provider:   "lrclib",
			TrackName:  c.TrackName,
			ArtistName: c.ArtistName,
			Duration:   int(c.Duration),
			Content:    content,
			Source:     source,
		})
	}

	return results, nil
}

func (p *LrclibProvider) Fetch(ctx context.Context, track *domain.TrackDTO) (*domain.Lyric, error) {
	p.logger.Debug("lrclib: fetching lyrics", "track_id", track.ID, "title", track.Title)
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

	lyric, err := p.exactGet(ctx, track.ID, normTitle, normArtist, albumName, track.Duration)
	if err != nil {
		return nil, err
	}
	if lyric != nil {
		p.logger.Debug("lrclib: fetch found exact match (with album)")
		return lyric, nil
	}

	if albumName != "" {
		lyric, err = p.exactGet(ctx, track.ID, normTitle, normArtist, "", track.Duration)
		if err != nil {
			return nil, err
		}
		if lyric != nil {
			p.logger.Debug("lrclib: fetch found exact match (without album)")
			return lyric, nil
		}
	}

	p.logger.Debug("lrclib: no exact match, performing search and rank")
	return p.searchAndRank(ctx, track.ID, normTitle, normArtist, track.Duration)
}

func (p *LrclibProvider) exactGet(ctx context.Context, trackID, title, artist, album string, duration int) (*domain.Lyric, error) {
	p.logger.Debug("lrclib: exact get", "title", title, "artist", artist, "album", album, "duration", duration)
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

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("lrclib request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

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
	return &domain.Lyric{TrackID: trackID, Content: content, Source: source}, nil
}

func (p *LrclibProvider) searchAndRank(ctx context.Context, trackID, normTitle, normArtist string, duration int) (*domain.Lyric, error) {
	p.logger.Debug("lrclib: search and rank", "title", normTitle, "artist", normArtist, "duration", duration)
	params := url.Values{}
	params.Set("track_name", normTitle)
	params.Set("artist_name", normArtist)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://lrclib.net/api/search?"+params.Encode(), nil)
	if err != nil {
		return nil, fmt.Errorf("failed to build lrclib search request: %w", err)
	}
	req.Header.Set("User-Agent", "Airmedy/1.0")

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("lrclib search request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

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

	p.logger.Debug("lrclib: search and rank found candidates", "count", len(candidates))

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
		p.logger.Debug("lrclib: no suitable candidate found in search and rank")
		return nil, nil
	}

	c := candidates[best]
	p.logger.Debug("lrclib: best candidate found", "track", c.TrackName, "artist", c.ArtistName, "score", bestScore)
	content, source := pickContent(c.SyncedLyrics, c.PlainLyrics)
	if content == "" {
		return nil, nil
	}
	return &domain.Lyric{TrackID: trackID, Content: content, Source: source}, nil
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
