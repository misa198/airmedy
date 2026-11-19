package lyrics

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"airmedy/internal/domain"
	"airmedy/internal/ttml"
)

// lrc.red lyrics provider (https://lrc.red/api).
//
// API terms: clients must identify themselves with a User-Agent naming the app
// and a link to contact the maintainers.
const (
	lrcredBaseURL = "https://lrc.red"
	// LrcredProviderName is the provider id used for settings toggles.
	LrcredProviderName = "lrcred"
	// lrcredDisplayName is shown to users as the source credit.
	lrcredDisplayName  = "lrc.red"
	lrcredProjectURL   = "https://github.com/misa198/airmedy"
	lrcredMaxResults   = 5
	lrcredMaxBodyBytes = 4 << 20
)

var isrcRe = regexp.MustCompile(`^[A-Za-z0-9]{12}$`)

func lrcredUserAgent() string {
	return "Airmedy/1.0 (+" + lrcredProjectURL + ")"
}

type lrcredHit struct {
	ISRC     string  `json:"isrc"`
	Title    string  `json:"title"`
	Artist   string  `json:"artist"`
	Album    string  `json:"album"`
	Duration float64 `json:"duration"`
}

type lrcredSearchResponse struct {
	Hits []lrcredHit `json:"hits"`
	Next string      `json:"next"`
}

type LrcredProvider struct {
	client   *http.Client
	logger   *slog.Logger
	settings domain.SettingsRepository
	baseURL  string
}

func NewLrcredProvider(logger *slog.Logger, settings domain.SettingsRepository) *LrcredProvider {
	return &LrcredProvider{
		client:   &http.Client{Timeout: 30 * time.Second},
		logger:   logger,
		settings: settings,
		baseURL:  lrcredBaseURL,
	}
}

// allowModelSyncs reports whether machine-generated (lrc:byModel) TTML timing
// may be used. Defaults to true when settings are unavailable.
func (p *LrcredProvider) allowModelSyncs(ctx context.Context) bool {
	if p.settings == nil {
		return true
	}
	s, err := p.settings.Load(ctx)
	if err != nil || s == nil {
		return true
	}
	return s.LrcredModelSyncs
}

func (p *LrcredProvider) Name() string { return LrcredProviderName }

func (p *LrcredProvider) get(ctx context.Context, path string) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, p.baseURL+path, nil)
	if err != nil {
		return nil, 0, fmt.Errorf("failed to build lrc.red request: %w", err)
	}
	req.Header.Set("User-Agent", lrcredUserAgent())
	resp, err := p.client.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("lrc.red request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, resp.StatusCode, nil
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, lrcredMaxBodyBytes))
	if err != nil {
		return nil, resp.StatusCode, fmt.Errorf("failed to read lrc.red response: %w", err)
	}
	return body, resp.StatusCode, nil
}

func (p *LrcredProvider) search(ctx context.Context, query string) ([]lrcredHit, error) {
	if strings.TrimSpace(query) == "" {
		// An empty query returns featured songs, which is never what we want.
		return nil, nil
	}
	body, status, err := p.get(ctx, "/search.json?"+url.Values{"q": {query}}.Encode())
	if err != nil {
		return nil, err
	}
	if status == http.StatusNotFound {
		return nil, nil
	}
	if status != http.StatusOK {
		return nil, fmt.Errorf("lrc.red search returned status %d", status)
	}
	var res lrcredSearchResponse
	if err := json.Unmarshal(body, &res); err != nil {
		return nil, fmt.Errorf("failed to parse lrc.red search response: %w", err)
	}
	return res.Hits, nil
}

var lrcredTimedLineRe = regexp.MustCompile(`(?m)^\[\d+:\d+`)

// lyricsFor fetches lyrics for an ISRC.
//
// TTML is always tried first (word/syllable timing, background vocals,
// translations; converted to enhanced LRC), whether its timing is human-made
// or model-made. When model syncs are disabled and the TTML is marked
// `lrc:byModel="true"`, it is skipped and the .lrc is used instead. The .lrc
// is also the fallback when no usable TTML exists.
//
// Returns empty content when nothing is available.
func (p *LrcredProvider) lyricsFor(ctx context.Context, isrc string) (content, source string, err error) {
	isrc = strings.ToUpper(isrc)
	if !isrcRe.MatchString(isrc) {
		return "", "", nil
	}
	escaped := url.PathEscape(isrc)

	if content, source, err = p.fetchTTML(ctx, escaped, p.allowModelSyncs(ctx)); err != nil || content != "" {
		return content, source, err
	}
	return p.fetchLRC(ctx, escaped)
}

func (p *LrcredProvider) fetchTTML(ctx context.Context, escapedISRC string, allowModel bool) (content, source string, err error) {
	body, status, err := p.get(ctx, "/s/"+escapedISRC+".ttml")
	if err != nil {
		return "", "", err
	}
	if status == http.StatusNotFound {
		return "", "", nil
	}
	if status != http.StatusOK {
		return "", "", fmt.Errorf("lrc.red TTML returned status %d", status)
	}
	if !allowModel && ttml.IsModelSynced(string(body)) {
		p.logger.Debug("lrc.red: skipping model-synced TTML", "isrc", escapedISRC)
		return "", "", nil
	}
	lrc, synced, convErr := ttml.ToLRC(string(body))
	if convErr != nil {
		p.logger.Debug("lrc.red: failed to convert TTML", "isrc", escapedISRC, "error", convErr)
		return "", "", nil
	}
	if strings.TrimSpace(lrc) == "" {
		return "", "", nil
	}
	if synced {
		return lrc, "lrcred-synced", nil
	}
	return lrc, "lrcred-plain", nil
}

func (p *LrcredProvider) fetchLRC(ctx context.Context, escapedISRC string) (content, source string, err error) {
	body, status, err := p.get(ctx, "/s/"+escapedISRC+".lrc")
	if err != nil {
		return "", "", err
	}
	if status == http.StatusNotFound {
		return "", "", nil
	}
	if status != http.StatusOK {
		return "", "", fmt.Errorf("lrc.red LRC returned status %d", status)
	}
	lrc := strings.TrimSpace(strings.TrimPrefix(string(body), "\ufeff"))
	if !lrcredTimedLineRe.MatchString(lrc) {
		return "", "", nil
	}
	return lrc, "lrcred-synced", nil
}

func (p *LrcredProvider) Search(ctx context.Context, title, artist string, duration int) ([]*domain.LyricsSearchResult, error) {
	p.logger.Debug("lrc.red: searching lyrics", "title", title, "artist", artist, "duration", duration)
	hits, err := p.search(ctx, strings.TrimSpace(artist+" "+title))
	if err != nil {
		return nil, err
	}

	var results []*domain.LyricsSearchResult
	for _, h := range hits {
		if len(results) >= lrcredMaxResults {
			break
		}
		content, source, err := p.lyricsFor(ctx, h.ISRC)
		if err != nil {
			p.logger.Debug("lrc.red: failed to fetch candidate", "isrc", h.ISRC, "error", err)
			continue
		}
		if content == "" {
			continue
		}
		results = append(results, &domain.LyricsSearchResult{
			Provider:   lrcredDisplayName,
			ID:         h.ISRC,
			TrackName:  h.Title,
			ArtistName: h.Artist,
			AlbumName:  h.Album,
			Duration:   int(h.Duration + 0.5),
			Content:    content,
			Source:     source,
		})
	}
	return results, nil
}

func (p *LrcredProvider) Fetch(ctx context.Context, track *domain.TrackDTO) (*domain.Lyric, error) {
	p.logger.Debug("lrc.red: fetching lyrics", "track_id", track.ID, "title", track.Title, "isrc", track.ISRC)

	// 1. Exact lookup by ISRC from the file's metadata.
	if isrcRe.MatchString(strings.TrimSpace(track.ISRC)) {
		content, source, err := p.lyricsFor(ctx, strings.TrimSpace(track.ISRC))
		if err != nil {
			return nil, err
		}
		if content != "" {
			p.logger.Debug("lrc.red: fetch found ISRC match")
			return &domain.Lyric{TrackID: track.ID, Content: content, Source: source}, nil
		}
	}

	// 2. Search and rank by title/artist/duration.
	artistRaw := track.RawArtistNames
	if len(track.Artists) > 0 && track.Artists[0] != nil {
		artistRaw = track.Artists[0].Name
	}
	cleanTitle, _ := extractFeatured(track.Title)
	normTitle := normalizeText(cleanTitle)
	normArtist := normalizeText(artistRaw)

	hits, err := p.search(ctx, strings.TrimSpace(artistRaw+" "+cleanTitle))
	if err != nil {
		return nil, err
	}

	best, bestScore := -1, -1.0
	for i, h := range hits {
		score := scoreCandidate(h.Title, h.Artist, h.Duration, normTitle, normArtist, track.Duration)
		if score > bestScore {
			best, bestScore = i, score
		}
	}
	if best < 0 {
		p.logger.Debug("lrc.red: no suitable candidate found")
		return nil, nil
	}

	h := hits[best]
	p.logger.Debug("lrc.red: best candidate", "isrc", h.ISRC, "track", h.Title, "artist", h.Artist, "score", bestScore)
	content, source, err := p.lyricsFor(ctx, h.ISRC)
	if err != nil || content == "" {
		return nil, err
	}
	return &domain.Lyric{TrackID: track.ID, Content: content, Source: source}, nil
}
