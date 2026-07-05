package playlist

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"mime"
	"os"
	"path/filepath"
	"strings"
	"time"

	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"

	"github.com/google/uuid"
	"github.com/misa198/lexorank-go"
)

type PlaylistService struct {
	repo          domain.PlaylistRepository
	trackRepo     domain.TrackRepository
	artworkCache  domain.ArtworkCache
	searchService domain.SearchService
	logger        *slog.Logger
}

func NewPlaylistService(repo domain.PlaylistRepository, trackRepo domain.TrackRepository, artworkCache domain.ArtworkCache, searchService domain.SearchService, logger *slog.Logger) *PlaylistService {
	return &PlaylistService{
		repo:          repo,
		trackRepo:     trackRepo,
		artworkCache:  artworkCache,
		searchService: searchService,
		logger:        logger,
	}
}

func (s *PlaylistService) Create(ctx context.Context, name, description string) (*domain.Playlist, error) {
	if name == "" {
		return nil, fmt.Errorf("playlist name cannot be empty")
	}
	p := &domain.Playlist{
		ID:          uuid.New().String(),
		Name:        name,
		Description: description,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
	}
	if err := s.repo.Save(ctx, p); err != nil {
		return nil, err
	}
	if err := s.searchService.IndexPlaylist(ctx, p); err != nil {
		s.logger.Warn("Failed to index playlist", "name", p.Name, "error", err)
	}
	return p, nil
}

func (s *PlaylistService) Update(ctx context.Context, id, name, description string) error {
	if name == "" {
		return fmt.Errorf("playlist name cannot be empty")
	}
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", id)
	}
	p.Name = name
	p.Description = description
	p.UpdatedAt = time.Now()
	if err := s.repo.Update(ctx, p); err != nil {
		return err
	}
	if err := s.searchService.IndexPlaylist(ctx, p); err != nil {
		s.logger.Warn("Failed to index playlist", "name", p.Name, "error", err)
	}
	return nil
}

func (s *PlaylistService) Delete(ctx context.Context, id string) error {
	if err := s.repo.Delete(ctx, id); err != nil {
		return err
	}
	return s.searchService.DeleteFromIndex(ctx, id)
}

func (s *PlaylistService) GetAll(ctx context.Context) ([]*domain.Playlist, error) {
	return s.repo.GetAll(ctx)
}

func (s *PlaylistService) GetByID(ctx context.Context, id string) (*domain.Playlist, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *PlaylistService) GetTracks(ctx context.Context, playlistID string) ([]*domain.TrackDTO, error) {
	p, err := s.repo.GetByID(ctx, playlistID)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return nil, fmt.Errorf("playlist not found: %s", playlistID)
	}
	if p.IsSmart {
		return s.evaluateSmartTracks(ctx, p)
	}
	return s.repo.GetTracks(ctx, playlistID)
}

// GetTracksPreview returns at most limit tracks — for callers that only need
// a handful (e.g. a 4-track artwork mosaic), so a live-updating smart
// playlist with a broad or unlimited match (mood playlists default to no
// cap) doesn't run/serialize its full result just to discard most of it.
func (s *PlaylistService) GetTracksPreview(ctx context.Context, playlistID string, limit int) ([]*domain.TrackDTO, error) {
	p, err := s.repo.GetByID(ctx, playlistID)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return nil, fmt.Errorf("playlist not found: %s", playlistID)
	}
	if !p.IsSmart {
		return s.repo.GetTracksPreview(ctx, playlistID, limit)
	}
	var config domain.SmartPlaylistConfig
	if p.Rules != nil && *p.Rules != "" {
		if err := json.Unmarshal([]byte(*p.Rules), &config); err != nil {
			return nil, fmt.Errorf("failed to unmarshal playlist rules: %w", err)
		}
	}
	if !config.LiveUpdating {
		return s.repo.GetTracksPreview(ctx, playlistID, limit)
	}
	return s.matchSmartConfigCapped(ctx, config, limit)
}

// CreateSmart creates a smart playlist backed by a rule set instead of a
// fixed track list — membership is computed at read time by GetTracks
// (or, when config.LiveUpdating is false, frozen at save time — see
// applySmartConfig).
func (s *PlaylistService) CreateSmart(ctx context.Context, name, description string, config domain.SmartPlaylistConfig) (*domain.Playlist, error) {
	if name == "" {
		return nil, fmt.Errorf("playlist name cannot be empty")
	}
	rulesJSON, err := marshalRules(config)
	if err != nil {
		return nil, err
	}
	p := &domain.Playlist{
		ID:          uuid.New().String(),
		Name:        name,
		Description: description,
		IsSmart:     true,
		Rules:       rulesJSON,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
	}
	if err := s.repo.Save(ctx, p); err != nil {
		return nil, err
	}
	if err := s.applySmartConfig(ctx, p.ID, config); err != nil {
		return nil, err
	}
	s.logger.Debug("Created smart playlist",
		"playlist_id", p.ID, "name", p.Name,
		"rule_count", countRules(config.Root), "live_updating", config.LiveUpdating,
		"limit_enabled", config.Limit.Enabled, "limit_count", config.Limit.Count, "limit_by", config.Limit.By)
	if err := s.searchService.IndexPlaylist(ctx, p); err != nil {
		s.logger.Warn("Failed to index playlist", "name", p.Name, "error", err)
	}
	return p, nil
}

// UpdateSmartRules replaces a smart playlist's rule set.
func (s *PlaylistService) UpdateSmartRules(ctx context.Context, id string, config domain.SmartPlaylistConfig) error {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", id)
	}
	if !p.IsSmart {
		return fmt.Errorf("playlist is not a smart playlist: %s", id)
	}
	rulesJSON, err := marshalRules(config)
	if err != nil {
		return err
	}
	if err := s.repo.UpdateRules(ctx, id, rulesJSON); err != nil {
		return err
	}
	s.logger.Debug("Updated smart playlist rules",
		"playlist_id", id,
		"rule_count", countRules(config.Root), "live_updating", config.LiveUpdating,
		"limit_enabled", config.Limit.Enabled, "limit_count", config.Limit.Count, "limit_by", config.Limit.By)
	return s.applySmartConfig(ctx, id, config)
}

// maxSmartPlaylistRules mirrors the frontend's MAX_RULES cap (see
// smartPlaylistFields.ts) — enforced here too since the frontend limit is
// UX only, not a security boundary.
const maxSmartPlaylistRules = 16

func marshalRules(config domain.SmartPlaylistConfig) (*string, error) {
	// Validate against the field/operator allowlist before persisting, so a
	// bad rule tree fails at create/update time rather than at every read.
	if _, _, err := BuildWhereClause(config.Root); err != nil {
		return nil, fmt.Errorf("invalid rule: %w", err)
	}
	ruleCount := countRules(config.Root)
	if ruleCount == 0 {
		return nil, fmt.Errorf("at least one rule is required")
	}
	if ruleCount > maxSmartPlaylistRules {
		return nil, fmt.Errorf("too many rules: %d (max %d)", ruleCount, maxSmartPlaylistRules)
	}
	if config.Limit.Enabled {
		if config.Limit.Count <= 0 {
			return nil, fmt.Errorf("limit count must be positive when enabled")
		}
		if _, err := OrderBySQL(config.Limit.By); err != nil {
			return nil, fmt.Errorf("invalid limit: %w", err)
		}
	}
	data, err := json.Marshal(config)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal rules: %w", err)
	}
	s := string(data)
	return &s, nil
}

// applySmartConfig materializes a snapshot into playlist_tracks when
// LiveUpdating is off, so evaluateSmartTracks can serve a frozen membership
// without re-running the rule tree. Any previous snapshot is cleared first —
// when LiveUpdating is on there is nothing to serve from playlist_tracks, so
// clearing it (and not re-populating) is correct there too.
func (s *PlaylistService) applySmartConfig(ctx context.Context, playlistID string, config domain.SmartPlaylistConfig) error {
	if err := s.repo.ClearTracks(ctx, playlistID); err != nil {
		return fmt.Errorf("failed to clear smart playlist snapshot: %w", err)
	}
	if config.LiveUpdating {
		s.logger.Debug("Smart playlist is live-updating, skipping snapshot materialization", "playlist_id", playlistID)
		return nil
	}
	tracks, err := s.matchSmartConfig(ctx, config)
	if err != nil {
		return fmt.Errorf("failed to compute smart playlist snapshot: %w", err)
	}
	ids := make([]string, len(tracks))
	for i, t := range tracks {
		ids[i] = t.ID
	}
	s.logger.Debug("Materialized smart playlist snapshot", "playlist_id", playlistID, "track_count", len(ids))
	return s.repo.AddTracks(ctx, playlistID, ids)
}

func (s *PlaylistService) matchSmartConfig(ctx context.Context, config domain.SmartPlaylistConfig) ([]*domain.TrackDTO, error) {
	return s.matchSmartConfigCapped(ctx, config, 0)
}

// matchSmartConfigCapped is matchSmartConfig with an additional hard ceiling
// on the SQL LIMIT — maxLimit <= 0 means "no extra ceiling, use the config's
// own limit as-is" (matchSmartConfig's behavior). GetTracksPreview uses a
// small maxLimit (e.g. 4, for an artwork mosaic) so a broad or unlimited
// smart playlist doesn't materialize/serialize its entire match just to
// throw away all but a handful of rows.
func (s *PlaylistService) matchSmartConfigCapped(ctx context.Context, config domain.SmartPlaylistConfig, maxLimit int) ([]*domain.TrackDTO, error) {
	whereSQL, args, err := BuildWhereClause(config.Root)
	if err != nil {
		return nil, fmt.Errorf("failed to evaluate playlist rules: %w", err)
	}
	limit := 0
	orderBy := ""
	if config.Limit.Enabled {
		limit = config.Limit.Count
		orderBy, err = OrderBySQL(config.Limit.By)
		if err != nil {
			return nil, fmt.Errorf("failed to evaluate playlist limit: %w", err)
		}
	}
	if maxLimit > 0 && (limit <= 0 || limit > maxLimit) {
		limit = maxLimit
	}
	return s.trackRepo.GetByRules(ctx, whereSQL, args, limit, orderBy)
}

func (s *PlaylistService) evaluateSmartTracks(ctx context.Context, p *domain.Playlist) ([]*domain.TrackDTO, error) {
	var config domain.SmartPlaylistConfig
	if p.Rules != nil && *p.Rules != "" {
		if err := json.Unmarshal([]byte(*p.Rules), &config); err != nil {
			return nil, fmt.Errorf("failed to unmarshal playlist rules: %w", err)
		}
	}
	if !config.LiveUpdating {
		s.logger.Debug("Evaluating smart playlist from frozen snapshot", "playlist_id", p.ID)
		return s.repo.GetTracks(ctx, p.ID)
	}
	tracks, err := s.matchSmartConfig(ctx, config)
	if err != nil {
		return nil, err
	}
	s.logger.Debug("Evaluated smart playlist rules live", "playlist_id", p.ID, "track_count", len(tracks))
	return tracks, nil
}

func (s *PlaylistService) AddTrack(ctx context.Context, playlistID, trackID string) error {
	if err := s.guardNotSmart(ctx, playlistID); err != nil {
		return err
	}
	maxRankStr, err := s.repo.GetMaxPosition(ctx, playlistID)
	if err != nil {
		return err
	}

	var newRank lexorank.Rank
	if maxRankStr == "" {
		newRank = lexorank.Middle()
	} else {
		maxRank, err := lexorank.ParseRank(maxRankStr)
		if err != nil {
			return err
		}
		newRank = maxRank.GenNext()
	}

	s.logger.Debug("Adding track to playlist with LexoRank",
		"playlist_id", playlistID,
		"track_id", trackID,
		"new_rank", newRank.String(),
		"prev_max", maxRankStr)

	return s.repo.AddTrack(ctx, playlistID, trackID, newRank.String())
}

func (s *PlaylistService) AddTracks(ctx context.Context, playlistID string, trackIDs []string) error {
	if err := s.guardNotSmart(ctx, playlistID); err != nil {
		return err
	}
	return s.repo.AddTracks(ctx, playlistID, trackIDs)
}

func (s *PlaylistService) RemoveTrack(ctx context.Context, playlistID, trackID string) error {
	if err := s.guardNotSmart(ctx, playlistID); err != nil {
		return err
	}
	return s.repo.RemoveTrack(ctx, playlistID, trackID)
}

// guardNotSmart rejects manual track mutation on a smart playlist — its
// membership is computed from rules (see GetTracks/evaluateSmartTracks), so
// there is no track ordering or membership to hand-edit.
func (s *PlaylistService) guardNotSmart(ctx context.Context, playlistID string) error {
	p, err := s.repo.GetByID(ctx, playlistID)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", playlistID)
	}
	if p.IsSmart {
		return fmt.Errorf("cannot manually edit tracks of a smart playlist: %s", playlistID)
	}
	return nil
}

func (s *PlaylistService) MoveTrack(ctx context.Context, playlistID, trackID, prevTrackID, nextTrackID string) error {
	if err := s.guardNotSmart(ctx, playlistID); err != nil {
		return err
	}
	var prevRank, nextRank lexorank.Rank
	var hasPrev, hasNext bool
	var err error

	if prevTrackID != "" {
		prevRankStr, err := s.repo.GetTrackPosition(ctx, playlistID, prevTrackID)
		if err != nil {
			return err
		}
		prevRank, err = lexorank.ParseRank(prevRankStr)
		if err != nil {
			return err
		}
		hasPrev = true
	}

	if nextTrackID != "" {
		nextRankStr, err := s.repo.GetTrackPosition(ctx, playlistID, nextTrackID)
		if err != nil {
			return err
		}
		nextRank, err = lexorank.ParseRank(nextRankStr)
		if err != nil {
			return err
		}
		hasNext = true
	}

	var newRank lexorank.Rank
	if !hasPrev {
		// Move to start
		if !hasNext {
			newRank = lexorank.Middle()
		} else {
			newRank = nextRank.GenPrev()
		}
	} else if !hasNext {
		// Move to end
		newRank = prevRank.GenNext()
	} else {
		// Move between
		newRank, err = prevRank.Between(nextRank)
		if err != nil {
			return err
		}
	}

	newRankStr := newRank.String()
	s.logger.Debug("Moving track in playlist",
		"playlist_id", playlistID,
		"track_id", trackID,
		"prev_track_id", prevTrackID,
		"next_track_id", nextTrackID,
		"new_rank", newRankStr)

	if err := s.repo.UpdateTrackPosition(ctx, playlistID, trackID, newRankStr); err != nil {
		return err
	}

	// Rebalance if rank string becomes too long
	if len(newRankStr) > 10 {
		s.logger.Info("Triggering LexoRank rebalance", "playlist_id", playlistID, "rank_length", len(newRankStr))
		return s.rebalanceRanks(ctx, playlistID)
	}

	return nil
}

func (s *PlaylistService) rebalanceRanks(ctx context.Context, playlistID string) error {
	tracks, err := s.repo.GetTracks(ctx, playlistID)
	if err != nil {
		return err
	}

	s.logger.Debug("Rebalancing playlist ranks", "playlist_id", playlistID, "track_count", len(tracks))

	updates := make(map[string]string)
	rank := lexorank.Middle()
	for _, t := range tracks {
		updates[t.ID] = rank.String()
		rank = rank.GenNext()
	}

	return s.repo.UpdateTracksPositions(ctx, playlistID, updates)
}

func (s *PlaylistService) SetArtwork(ctx context.Context, id, imagePath string) (*string, error) {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if p == nil {
		return nil, fmt.Errorf("playlist not found: %s", id)
	}

	data, err := os.ReadFile(imagePath)
	if err != nil {
		return nil, fmt.Errorf("failed to read image: %w", err)
	}

	ext := filepath.Ext(imagePath)
	mimeType := mime.TypeByExtension(ext)
	if mimeType == "" {
		mimeType = "image/jpeg"
	}

	key, err := s.artworkCache.Save(ctx, data, mimeType)
	if err != nil {
		return nil, fmt.Errorf("failed to save artwork: %w", err)
	}

	p.ArtworkKey = &key
	if err := s.repo.Update(ctx, p); err != nil {
		return nil, err
	}

	return &key, nil
}

func (s *PlaylistService) RemoveArtwork(ctx context.Context, id string) error {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", id)
	}

	p.ArtworkKey = nil
	return s.repo.Update(ctx, p)
}

func (s *PlaylistService) GetPlaylistsForTrack(ctx context.Context, trackID string) ([]string, error) {
	return s.repo.GetPlaylistsForTrack(ctx, trackID)
}

func (s *PlaylistService) TogglePinned(ctx context.Context, id string) (bool, error) {
	return s.repo.TogglePinned(ctx, id)
}

func (s *PlaylistService) ExportM3U8(ctx context.Context, playlistID string, destPath string) error {
	p, err := s.repo.GetByID(ctx, playlistID)
	if err != nil {
		return fmt.Errorf("get playlist: %w", err)
	}
	if p == nil {
		return fmt.Errorf("playlist not found: %s", playlistID)
	}

	tracks, err := s.repo.GetTracks(ctx, playlistID)
	if err != nil {
		return fmt.Errorf("get tracks: %w", err)
	}

	var buf bytes.Buffer
	buf.WriteString("#EXTM3U\n")
	buf.WriteString("#EXTENC:UTF-8\n")
	fmt.Fprintf(&buf, "#PLAYLIST:%s\n", p.Name)

	for _, t := range tracks {
		if t == nil {
			continue
		}
		artist := t.RawArtistNames
		title := t.Title
		album := ""
		if t.Album != nil {
			album = t.Album.Title
		}
		genre := t.RawGenreNames

		displayName := title
		if artist != "" {
			displayName = artist + " - " + title
		}

		fmt.Fprintf(&buf, "#EXTINF:%d,%s\n", t.Duration, displayName)
		if album != "" {
			fmt.Fprintf(&buf, "#EXTALB:%s\n", album)
		}
		if artist != "" {
			fmt.Fprintf(&buf, "#EXTART:%s\n", artist)
		}
		if genre != "" {
			fmt.Fprintf(&buf, "#EXTGENRE:%s\n", strings.SplitN(genre, ";", 2)[0])
		}
		buf.WriteString(t.Path + "\n")
	}

	if err := os.WriteFile(destPath, buf.Bytes(), 0644); err != nil {
		return fmt.Errorf("write file: %w", err)
	}
	return nil
}

func (s *PlaylistService) GetPlaylistColors(ctx context.Context, id string) (*domain.ThemeColors, error) {
	p, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if p == nil || p.ArtworkKey == nil || *p.ArtworkKey == "" {
		return nil, nil
	}

	path := s.artworkCache.GetPath(*p.ArtworkKey)
	if _, err := os.Stat(path); os.IsNotExist(err) {
		return nil, nil
	}

	colors, err := artwork.ExtractPalette(path)
	if err != nil {
		return nil, fmt.Errorf("failed to extract palette: %w", err)
	}

	return colors, nil
}
