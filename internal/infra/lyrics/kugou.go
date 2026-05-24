// Kugou lyrics provider. Ported from the original Python implementation by
// github.com/1053278842 (https://github.com/1053278842/kugou_lyric_api).
package lyrics

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"math"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"time"

	"airmedy/internal/domain"
)

// Matches bracketed content: ASCII, fullwidth, and CJK bracket pairs.
var kugouBracketRe = regexp.MustCompile(`[(（\x{3010}\x{3014}\[{｛][^)）\x{3011}\x{3015}\]}｝]*[)）\x{3011}\x{3015}\]}｝]`)

type kugouCandidate struct {
	ID        string `json:"id"`
	AccessKey string `json:"accesskey"`
	Duration  int    `json:"duration"` // milliseconds
	Score     int    `json:"score"`
}

type kugouSearchResponse struct {
	Candidates []kugouCandidate `json:"candidates"`
}

type kugouDownloadResponse struct {
	Content string `json:"content"` // base64-encoded LRC
}

type KugouProvider struct {
	client *http.Client
	logger *slog.Logger
}

func NewKugouProvider(logger *slog.Logger) *KugouProvider {
	return &KugouProvider{
		client: &http.Client{Timeout: 30 * time.Second},
		logger: logger,
	}
}

func (p *KugouProvider) Name() string { return "kugou" }

func (p *KugouProvider) Search(ctx context.Context, title, artist string, duration int) ([]*domain.LyricsSearchResult, error) {
	p.logger.Debug("kugou: searching lyrics", "title", title, "artist", artist, "duration", duration)
	keyword := artist + " - " + title
	durationMs := duration * 1000
	candidates, err := p.kugouSearch(ctx, keyword, durationMs)
	if err != nil {
		return nil, err
	}

	p.logger.Debug("kugou: search found candidates", "count", len(candidates))

	var results []*domain.LyricsSearchResult
	// Only fetch content for top 5 candidates to avoid excessive requests
	maxResults := 5
	if len(candidates) < maxResults {
		maxResults = len(candidates)
	}

	for i := range maxResults {
		c := &candidates[i]
		content, err := p.kugouDownload(ctx, c.ID, c.AccessKey)
		if err != nil {
			p.logger.Debug("kugou: failed to download candidate", "id", c.ID, "error", err)
			continue
		}
		if content == "" {
			continue
		}

		source := "kugou-plain"
		if kugouIsSynced(content) {
			source = "kugou-synced"
		}

		results = append(results, &domain.LyricsSearchResult{
			Provider:   "kugou",
			ID:         c.ID,
			TrackName:  title,  // Kugou search results don't return track name/artist name for each candidate
			ArtistName: artist, // using the search terms as placeholders
			Duration:   c.Duration / 1000,
			Content:    content,
			Source:     source,
		})
	}

	p.logger.Debug("kugou: search processed results", "count", len(results))
	return results, nil
}

func (p *KugouProvider) Fetch(ctx context.Context, track *domain.TrackDTO) (*domain.Lyric, error) {
	p.logger.Debug("kugou: fetching lyrics", "track_id", track.ID, "title", track.Title)
	artistRaw := track.RawArtistNames
	if len(track.Artists) > 0 && track.Artists[0] != nil {
		artistRaw = track.Artists[0].Name
	}

	cleanTitle, _ := extractFeatured(track.Title)
	title := normalizeText(cleanTitle)
	artist := normalizeText(artistRaw)
	durationMs := track.Duration * 1000

	for attempt := range 3 {
		keyword := artist + " - " + title
		p.logger.Debug("kugou: fetch attempt", "attempt", attempt, "keyword", keyword)
		candidates, err := p.kugouSearch(ctx, keyword, durationMs)
		if err != nil {
			return nil, err
		}

		if len(candidates) > 0 {
			p.logger.Debug("kugou: fetch found candidates", "count", len(candidates))
			best := p.selectBestCandidate(candidates, track.Duration)
			if best != nil {
				p.logger.Debug("kugou: best candidate selected", "id", best.ID, "score", best.Score)
				content, err := p.kugouDownload(ctx, best.ID, best.AccessKey)
				if err != nil {
					return nil, err
				}
				if content != "" {
					source := "kugou-plain"
					if kugouIsSynced(content) {
						source = "kugou-synced"
					}
					return &domain.Lyric{TrackID: track.ID, Content: content, Source: source}, nil
				}
			}
		}

		switch attempt {
		case 0:
			title = kugouRemoveBrackets(title)
			artist = kugouRemoveBrackets(artist)
		case 1:
			title, artist = artist, title
		}
	}
	p.logger.Debug("kugou: fetch failed to find suitable lyrics")
	return nil, nil
}

func (p *KugouProvider) kugouSearch(ctx context.Context, keyword string, durationMs int) ([]kugouCandidate, error) {
	p.logger.Debug("kugou: performing search", "keyword", keyword, "duration_ms", durationMs)
	params := url.Values{}
	params.Set("ver", "1")
	params.Set("man", "yes")
	params.Set("client", "mobi")
	params.Set("keyword", keyword)
	if durationMs > 0 {
		params.Set("duration", strconv.Itoa(durationMs))
	}
	params.Set("hash", "")
	params.Set("album_audio_id", "")

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "http://krcs.kugou.com/search?"+params.Encode(), nil)
	if err != nil {
		return nil, fmt.Errorf("kugou: failed to build search request: %w", err)
	}

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("kugou: search request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		p.logger.Debug("kugou: search returned non-OK status", "status", resp.StatusCode)
		return nil, nil
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("kugou: failed to read search response: %w", err)
	}

	var result kugouSearchResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("kugou: failed to parse search response: %w", err)
	}
	return result.Candidates, nil
}

func (p *KugouProvider) selectBestCandidate(candidates []kugouCandidate, trackDurationSec int) *kugouCandidate {
	p.logger.Debug("kugou: selecting best candidate", "count", len(candidates), "target_duration", trackDurationSec)
	var best *kugouCandidate
	bestDurDiff := math.MaxInt32
	bestScore := math.MinInt32

	for i := range candidates {
		c := &candidates[i]
		if trackDurationSec > 0 {
			durDiff := absInt(c.Duration/1000 - trackDurationSec)
			if best == nil || durDiff < bestDurDiff || (durDiff == bestDurDiff && c.Score > bestScore) {
				best = c
				bestDurDiff = durDiff
				bestScore = c.Score
			}
		} else {
			if best == nil || c.Score > bestScore {
				best = c
				bestScore = c.Score
			}
		}
	}
	return best
}

func (p *KugouProvider) kugouDownload(ctx context.Context, id, accesskey string) (string, error) {
	p.logger.Debug("kugou: downloading lyrics", "id", id)
	params := url.Values{}
	params.Set("ver", "1")
	params.Set("client", "pc")
	params.Set("id", id)
	params.Set("accesskey", accesskey)
	params.Set("fmt", "lrc")
	params.Set("charset", "utf8")

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, "https://lyrics.kugou.com/download?"+params.Encode(), nil)
	if err != nil {
		return "", fmt.Errorf("kugou: failed to build download request: %w", err)
	}

	resp, err := p.client.Do(req)
	if err != nil {
		return "", fmt.Errorf("kugou: download request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.StatusCode != http.StatusOK {
		p.logger.Debug("kugou: download returned non-OK status", "status", resp.StatusCode)
		return "", nil
	}

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("kugou: failed to read download response: %w", err)
	}

	var result kugouDownloadResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return "", fmt.Errorf("kugou: failed to parse download response: %w", err)
	}

	decoded, err := base64.StdEncoding.DecodeString(result.Content)
	if err != nil {
		return "", fmt.Errorf("kugou: failed to decode lyrics content: %w", err)
	}

	return string(decoded), nil
}

var lrcTimestampRe = regexp.MustCompile(`(?m)^\[\d{2}:\d{2}\.\d+\]`)

func kugouIsSynced(content string) bool {
	return lrcTimestampRe.MatchString(content)
}

func kugouRemoveBrackets(text string) string {
	return kugouBracketRe.ReplaceAllString(text, "")
}

func absInt(x int) int {
	if x < 0 {
		return -x
	}
	return x
}
