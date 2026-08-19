package mobilesync

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"
	"os"
	"sort"
	"strings"
	"sync"
	"time"

	lyricsapp "airmedy/internal/app/lyrics"
	playlistapp "airmedy/internal/app/playlist"
	"airmedy/internal/domain"

	"github.com/google/uuid"
)

const (
	syncProtocolVersion = 1
	syncRequestType     = "library.sync.request"
	syncReceiptType     = "library.sync.receipt"
)

type syncRequest struct {
	Version      int    `json:"version"`
	Type         string `json:"type"`
	PlanID       string `json:"plan_id"`
	DesktopID    string `json:"desktop_id"`
	MobileID     string `json:"mobile_id"`
	ManifestURL  string `json:"manifest_url"`
	ManifestHash string `json:"manifest_hash"`
	IssuedAt     int64  `json:"issued_at"`
	Signature    string `json:"signature"`
}

type syncReceipt struct {
	Version   int    `json:"version"`
	Type      string `json:"type"`
	PlanID    string `json:"plan_id"`
	MobileID  string `json:"mobile_id"`
	AssetID   string `json:"asset_id"`
	Complete  bool   `json:"complete"`
	IssuedAt  int64  `json:"issued_at"`
	Signature string `json:"signature"`
}

const (
	playlistReconciliationRequestType = "playlist.sync.reconcile.request"
	playlistReconciliationResultType  = "playlist.sync.reconcile.result"
)

var reconciliationTimeout = 30 * time.Second

const maxReconciliationBodySize = 32 << 20

type playlistReconciliationRequest struct {
	Version          int                           `json:"version"`
	Type             string                        `json:"type"`
	ReconciliationID string                        `json:"reconciliation_id"`
	DesktopID        string                        `json:"desktop_id"`
	MobileID         string                        `json:"mobile_id"`
	Scope            domain.MobileLibrarySyncScope `json:"scope"`
	BatchURL         string                        `json:"batch_url"`
	ArtworkURL       string                        `json:"artwork_url"`
	ListeningURL     string                        `json:"listening_url"`
	IssuedAt         int64                         `json:"issued_at"`
	Signature        string                        `json:"signature"`
}

type playlistReconciliationResult struct {
	Version          int                      `json:"version"`
	Type             string                   `json:"type"`
	ReconciliationID string                   `json:"reconciliation_id"`
	MobileID         string                   `json:"mobile_id"`
	Results          []playlistMutationResult `json:"results"`
	IssuedAt         int64                    `json:"issued_at"`
	Signature        string                   `json:"signature"`
}

type playlistReconciliation struct {
	ID       string
	DeviceID string
	Scope    domain.MobileLibrarySyncScope
	Expires  time.Time
	result   chan playlistReconciliationResult
	artwork  map[string]string
}

type Service struct {
	plans          domain.MobileLibrarySyncPlanRepository
	tracks         domain.TrackRepository
	playlists      domain.PlaylistRepository
	artists        domain.ArtistRepository
	lyrics         *lyricsapp.LyricsService
	lyricCache     domain.MobileSyncLyricCacheRepository
	analysis       domain.AnalysisRepository
	artwork        domain.ArtworkCache
	devices        domain.TrustedMobileDeviceRepository
	identity       domain.PairingIdentityRepository
	keys           domain.PairingKeyStore
	broker         domain.PairingBroker
	ledger         domain.PlaylistMutationLedger
	lww            domain.PlaylistMutationLWW
	favoriteLedger domain.FavoriteMutationLedger
	favoriteLWW    domain.FavoriteMutationLWW
	staging        domain.PlaylistArtworkStagingRepository
	tx             domain.TxManager
	playlistSvc    *playlistapp.PlaylistService
	listening      domain.ListeningRepository
	logger         *slog.Logger

	mu                 sync.Mutex
	mutationMu         sync.Mutex
	server             *http.Server
	port               int
	nonces             map[string]time.Time
	playlistArtwork    map[string]string
	reconciliations    map[string]*playlistReconciliation
	reconciliationSub  bool
	receiptsSubscribed bool
	listeners          []func(*domain.MobileLibrarySyncPlan)
	starting           map[string]map[uint64]context.CancelFunc
	nextStartID        uint64
}

func NewService(plans domain.MobileLibrarySyncPlanRepository, tracks domain.TrackRepository, playlists domain.PlaylistRepository, artists domain.ArtistRepository, lyrics *lyricsapp.LyricsService, lyricCache domain.MobileSyncLyricCacheRepository, analysis domain.AnalysisRepository, artwork domain.ArtworkCache, devices domain.TrustedMobileDeviceRepository, identity domain.PairingIdentityRepository, keys domain.PairingKeyStore, broker domain.PairingBroker, ledger domain.PlaylistMutationLedger, lww domain.PlaylistMutationLWW, favoriteLedger domain.FavoriteMutationLedger, favoriteLWW domain.FavoriteMutationLWW, staging domain.PlaylistArtworkStagingRepository, tx domain.TxManager, playlistSvc *playlistapp.PlaylistService, listening domain.ListeningRepository, logger *slog.Logger) *Service {
	return &Service{plans: plans, tracks: tracks, playlists: playlists, artists: artists, lyrics: lyrics, lyricCache: lyricCache, analysis: analysis, artwork: artwork, devices: devices, identity: identity, keys: keys, broker: broker, ledger: ledger, lww: lww, favoriteLedger: favoriteLedger, favoriteLWW: favoriteLWW, staging: staging, tx: tx, playlistSvc: playlistSvc, listening: listening, logger: logger, nonces: make(map[string]time.Time), playlistArtwork: make(map[string]string), reconciliations: make(map[string]*playlistReconciliation)}
}

func (s *Service) OnStart(ctx context.Context) error {
	// HTTP plans and MQTT requests disappear when desktop exits, so an active
	// persisted plan can never resume after restart.
	if err := s.plans.MarkAllActiveSuperseded(ctx); err != nil {
		return fmt.Errorf("supersede interrupted mobile library sync plans: %w", err)
	}
	return s.cleanupPlaylistArtwork(ctx)
}

func (s *Service) OnStop(ctx context.Context) error {
	s.mu.Lock()
	server := s.server
	s.server = nil
	s.port = 0
	s.mu.Unlock()
	if server == nil {
		return nil
	}
	return server.Shutdown(ctx)
}

func (s *Service) AddListener(listener func(*domain.MobileLibrarySyncPlan)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.listeners = append(s.listeners, listener)
}

func (s *Service) emit(plan *domain.MobileLibrarySyncPlan) {
	s.mu.Lock()
	listeners := append([]func(*domain.MobileLibrarySyncPlan){}, s.listeners...)
	s.mu.Unlock()
	for _, listener := range listeners {
		listener(plan)
	}
}

func (s *Service) GetStatus(ctx context.Context, deviceID string) (*domain.MobileLibrarySyncPlan, error) {
	return s.plans.GetLatest(ctx, deviceID)
}

func (s *Service) startContext(ctx context.Context, deviceID string) (context.Context, func()) {
	ctx, cancel := context.WithCancel(ctx)
	s.mu.Lock()
	if s.starting == nil {
		s.starting = make(map[string]map[uint64]context.CancelFunc)
	}
	s.nextStartID++
	id := s.nextStartID
	if s.starting[deviceID] == nil {
		s.starting[deviceID] = make(map[uint64]context.CancelFunc)
	}
	s.starting[deviceID][id] = cancel
	s.mu.Unlock()
	return ctx, func() {
		cancel()
		s.mu.Lock()
		delete(s.starting[deviceID], id)
		if len(s.starting[deviceID]) == 0 {
			delete(s.starting, deviceID)
		}
		s.mu.Unlock()
	}
}

func (s *Service) cancelStarts(deviceID string) {
	s.mu.Lock()
	cancels := make([]context.CancelFunc, 0, len(s.starting[deviceID]))
	for _, cancel := range s.starting[deviceID] {
		cancels = append(cancels, cancel)
	}
	s.mu.Unlock()
	for _, cancel := range cancels {
		cancel()
	}
}

// Cancel makes the active plan unavailable to the passive mobile client.
func (s *Service) Cancel(ctx context.Context, deviceID string) (*domain.MobileLibrarySyncPlan, error) {
	if _, err := uuid.Parse(deviceID); err != nil {
		return nil, fmt.Errorf("invalid mobile device ID")
	}
	s.cancelStarts(deviceID)
	plan, err := s.plans.GetLatest(ctx, deviceID)
	if err != nil {
		return nil, err
	}
	if plan == nil {
		return nil, fmt.Errorf("no active mobile sync plan")
	}
	if plan.Status != "active" {
		return plan, nil
	}
	if err := s.plans.MarkSuperseded(ctx, deviceID); err != nil {
		return nil, fmt.Errorf("cancel mobile library sync: %w", err)
	}
	plan, err = s.plans.GetLatest(ctx, deviceID)
	if err != nil {
		return nil, err
	}
	s.emit(plan)
	return plan, nil
}

// CancelIfActive stops an in-flight plan without treating an absent or terminal plan as an error.
func (s *Service) CancelIfActive(ctx context.Context, deviceID string) error {
	s.cancelStarts(deviceID)
	plan, err := s.GetStatus(ctx, deviceID)
	if err != nil {
		return fmt.Errorf("get mobile library sync status: %w", err)
	}
	if plan == nil || plan.Status != "active" {
		return nil
	}
	if _, err := s.Cancel(ctx, deviceID); err != nil {
		return err
	}
	return nil
}

// Start starts a new immutable snapshot, or re-announces the active snapshot
// when the user presses Sync again with unchanged scope.
func (s *Service) Start(ctx context.Context, deviceID string, scope domain.MobileLibrarySyncScope, host string, replace bool) (*domain.MobileLibrarySyncPlan, error) {
	if _, err := uuid.Parse(deviceID); err != nil {
		return nil, fmt.Errorf("invalid mobile device ID")
	}
	if strings.TrimSpace(host) == "" || net.ParseIP(host) == nil {
		return nil, fmt.Errorf("invalid desktop sync address")
	}
	if err := s.normalizeScope(ctx, &scope); err != nil {
		return nil, err
	}
	ctx, done := s.startContext(ctx, deviceID)
	defer done()
	trusted, err := s.devices.GetByDeviceID(ctx, deviceID)
	if err != nil {
		return nil, fmt.Errorf("load trusted mobile device: %w", err)
	}
	if trusted == nil {
		return nil, fmt.Errorf("mobile device is not trusted")
	}
	if err := s.ensureHTTPServer(); err != nil {
		return nil, err
	}
	if err := s.reconcilePlaylists(ctx, deviceID, scope, host); err != nil {
		return nil, err
	}
	current, err := s.plans.GetLatest(ctx, deviceID)
	if err != nil {
		return nil, err
	}
	if current != nil && current.Status == "active" {
		if sameScope(current.Scope, scope) {
			return current, s.publishRequest(ctx, current, host)
		}
		if !replace {
			return nil, fmt.Errorf("an incomplete mobile sync plan must be replaced explicitly")
		}
		if err := s.plans.MarkSuperseded(ctx, deviceID); err != nil {
			return nil, err
		}
	}
	plan, err := s.createPlan(ctx, deviceID, scope)
	if err != nil {
		return nil, err
	}
	if err := s.ensureHTTPServer(); err != nil {
		return nil, err
	}
	if err := s.publishRequest(ctx, plan, host); err != nil {
		return nil, err
	}
	s.emit(plan)
	return plan, nil
}

func (s *Service) reconcilePlaylists(ctx context.Context, deviceID string, scope domain.MobileLibrarySyncScope, host string) error {
	identity, err := s.identity.Load(ctx)
	if err != nil {
		return fmt.Errorf("load pairing identity: %w", err)
	}
	if identity == nil {
		return fmt.Errorf("pairing identity unavailable")
	}
	if err := s.ensureReconciliationSubscription(ctx, identity.DeviceID); err != nil {
		return err
	}
	key, ok, err := s.keys.Load(ctx)
	if err != nil {
		return fmt.Errorf("load pairing key: %w", err)
	}
	if !ok {
		return fmt.Errorf("pairing key unavailable")
	}
	reconciliation := &playlistReconciliation{ID: uuid.NewString(), DeviceID: deviceID, Scope: scope, Expires: time.Now().UTC().Add(reconciliationTimeout), result: make(chan playlistReconciliationResult, 1), artwork: make(map[string]string)}
	s.mu.Lock()
	s.pruneReconciliationsLocked(time.Now().UTC())
	s.reconciliations[reconciliation.ID] = reconciliation
	s.mu.Unlock()
	defer func() {
		s.mu.Lock()
		delete(s.reconciliations, reconciliation.ID)
		s.mu.Unlock()
		if s.staging != nil {
			_, _ = s.staging.DeleteReconciliation(context.Background(), reconciliation.ID, deviceID)
		}
		if err := s.cleanupPlaylistArtwork(context.Background()); err != nil && s.logger != nil {
			s.logger.Warn("cleanup playlist reconciliation artwork", "error", err)
		}
	}()
	base := fmt.Sprintf("http://%s:%d/mobile-sync/v1/reconciliations/%s", host, s.port, reconciliation.ID)
	request := playlistReconciliationRequest{Version: playlistSyncVersion, Type: playlistReconciliationRequestType, ReconciliationID: reconciliation.ID, DesktopID: identity.DeviceID, MobileID: deviceID, Scope: scope, BatchURL: base + "/playlist-mutations", ArtworkURL: base + "/playlist-artwork", ListeningURL: base + "/listening", IssuedAt: time.Now().UTC().UnixMilli()}
	input, _ := json.Marshal(request)
	request.Signature = base64.RawURLEncoding.EncodeToString(ed25519.Sign(key, input))
	payload, _ := json.Marshal(request)
	if err := s.broker.Publish(ctx, playlistRequestTopic(identity.DeviceID, deviceID), payload); err != nil {
		return fmt.Errorf("publish playlist reconciliation request: %w", err)
	}
	timer := time.NewTimer(reconciliationTimeout)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return fmt.Errorf("playlist reconciliation timed out after %s", reconciliationTimeout)
	case <-reconciliation.result:
		return nil
	}
}

func sameScope(a, b domain.MobileLibrarySyncScope) bool {
	if a.Kind != b.Kind {
		return false
	}
	ax, bx := append([]string(nil), a.SelectedIDs...), append([]string(nil), b.SelectedIDs...)
	sort.Strings(ax)
	sort.Strings(bx)
	return strings.Join(ax, "\x00") == strings.Join(bx, "\x00")
}

func (s *Service) createPlan(ctx context.Context, deviceID string, scope domain.MobileLibrarySyncScope) (*domain.MobileLibrarySyncPlan, error) {
	if err := s.normalizeScope(ctx, &scope); err != nil {
		return nil, err
	}
	tracks, err := s.resolveScope(ctx, &scope)
	if err != nil {
		return nil, err
	}
	favorites, err := s.tracks.GetFavorites(ctx)
	if err != nil {
		return nil, fmt.Errorf("get favorite tracks for mobile sync: %w", err)
	}
	if scope.Kind != domain.MobileLibrarySyncScopeAll {
		tracks = mergeTracks(tracks, favorites)
	}
	planID := uuid.NewString()
	manifest := domain.MobileLibrarySyncManifest{Version: syncProtocolVersion, PlanID: planID, Scope: scope, Lyrics: map[string]*domain.Lyric{}, Analysis: map[string]*domain.TrackFeatures{}}
	assets := make([]domain.MobileLibrarySyncAsset, 0, len(tracks)*2)
	resolvedLyrics, err := s.lyrics.ResolveForMobileSync(ctx, tracks, s.lyricCache)
	if err != nil {
		return nil, fmt.Errorf("resolve sync lyrics: %w", err)
	}
	artworkKeys := map[string]struct{}{}
	selected := map[string]struct{}{}
	for _, dto := range tracks {
		selected[dto.ID] = struct{}{}
		asset, err := fileAsset(ctx, "audio:"+dto.ID, "audio", dto.Path)
		if err != nil {
			return nil, fmt.Errorf("snapshot audio %q: %w", dto.Title, err)
		}
		assets = append(assets, asset)
		copy := *dto
		copy.Track = dto.Track
		copy.Path = ""
		manifest.Tracks = append(manifest.Tracks, &copy)
		if lyric := resolvedLyrics[dto.ID]; lyric != nil {
			manifest.Lyrics[dto.ID] = lyric
		}
		if feature, err := s.analysis.GetFeatures(ctx, dto.ID); err != nil {
			return nil, fmt.Errorf("load track analysis: %w", err)
		} else if feature != nil {
			manifest.Analysis[dto.ID] = feature
		}
		collectTrackArtwork(dto, artworkKeys)
	}
	if err := s.addPlaylists(ctx, scope, selected, favorites, &manifest, artworkKeys); err != nil {
		return nil, err
	}
	keys := make([]string, 0, len(artworkKeys))
	for key := range artworkKeys {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	for _, key := range keys {
		if key == "" || !s.artwork.Exists(key) {
			continue
		}
		asset, err := fileAsset(ctx, "artwork:"+key, "artwork", s.artwork.GetPath(key))
		if err != nil {
			return nil, fmt.Errorf("snapshot artwork: %w", err)
		}
		assets = append(assets, asset)
	}
	manifest.Assets = assets
	unsigned, err := json.Marshal(manifest)
	if err != nil {
		return nil, fmt.Errorf("encode mobile sync manifest: %w", err)
	}
	contentSum := sha256.Sum256(unsigned)
	manifest.Revision = hex.EncodeToString(contentSum[:])
	encoded, err := marshalManifest(manifest)
	if err != nil {
		return nil, fmt.Errorf("encode mobile sync manifest: %w", err)
	}
	planSum := sha256.Sum256(encoded)
	now := time.Now().UTC()
	plan := &domain.MobileLibrarySyncPlan{ID: planID, DeviceID: deviceID, Scope: scope, Manifest: manifest, ManifestHash: hex.EncodeToString(planSum[:]), Status: "active", Total: len(assets), CreatedAt: now, UpdatedAt: now}
	if err := s.plans.Save(ctx, plan); err != nil {
		return nil, err
	}
	return plan, nil
}

// marshalManifest is the single wire representation used for the plan hash and
// the HTTP response. The mobile verifies the SHA-256 of the exact response
// bytes, so using json.Encoder here (which appends a newline) would invalidate
// every manifest before its first asset can be downloaded.
func marshalManifest(manifest domain.MobileLibrarySyncManifest) ([]byte, error) {
	return json.Marshal(manifest)
}

func (s *Service) resolveScope(ctx context.Context, scope *domain.MobileLibrarySyncScope) ([]*domain.TrackDTO, error) {
	var result []*domain.TrackDTO
	var err error
	switch scope.Kind {
	case domain.MobileLibrarySyncScopeAll:
		if len(scope.SelectedIDs) != 0 {
			return nil, fmt.Errorf("all-library sync cannot contain selections")
		}
		result, err = s.tracks.GetAll(ctx)
	case domain.MobileLibrarySyncScopeArtists:
		for _, id := range scope.SelectedIDs {
			rows, getErr := s.tracks.GetByArtistID(ctx, id)
			if getErr != nil {
				return nil, getErr
			}
			result = append(result, rows...)
		}
	case domain.MobileLibrarySyncScopeAlbums:
		for _, id := range scope.SelectedIDs {
			rows, getErr := s.tracks.GetByAlbumID(ctx, id)
			if getErr != nil {
				return nil, getErr
			}
			result = append(result, rows...)
		}
	case domain.MobileLibrarySyncScopeGenres:
		for _, id := range scope.SelectedIDs {
			rows, getErr := s.tracks.GetByGenreID(ctx, id)
			if getErr != nil {
				return nil, getErr
			}
			result = append(result, rows...)
		}
	case domain.MobileLibrarySyncScopePlaylists:
		for _, id := range scope.SelectedIDs {
			rows, getErr := s.playlists.GetTracks(ctx, id)
			if getErr != nil {
				return nil, getErr
			}
			result = append(result, rows...)
		}
	default:
		return nil, fmt.Errorf("invalid mobile library sync scope")
	}
	if err != nil {
		return nil, fmt.Errorf("resolve mobile library sync scope: %w", err)
	}
	if scope.Kind != domain.MobileLibrarySyncScopeAll && len(scope.SelectedIDs) == 0 {
		return nil, fmt.Errorf("select at least one item to sync")
	}
	return mergeTracks(result, nil), nil
}

// normalizeScope keeps the wire scope limited to user-selectable playlists.
// Favorites is synced separately and smart playlists are not mutable snapshots.
func (s *Service) normalizeScope(ctx context.Context, scope *domain.MobileLibrarySyncScope) error {
	scope.Kind = strings.ToLower(strings.TrimSpace(scope.Kind))
	sort.Strings(scope.SelectedIDs)
	if scope.Kind != domain.MobileLibrarySyncScopePlaylists {
		return nil
	}
	ids := make([]string, 0, len(scope.SelectedIDs))
	for _, id := range scope.SelectedIDs {
		playlist, err := s.playlists.GetByID(ctx, id)
		if err != nil {
			return fmt.Errorf("load playlist for mobile sync scope: %w", err)
		}
		if playlist != nil && playlist.ID != playlistapp.FavoritesPlaylistID && !playlist.IsSmart {
			ids = append(ids, id)
		}
	}
	scope.SelectedIDs = ids
	return nil
}

func mergeTracks(primary, extra []*domain.TrackDTO) []*domain.TrackDTO {
	seen := make(map[string]struct{}, len(primary)+len(extra))
	merged := make([]*domain.TrackDTO, 0, len(primary)+len(extra))
	for _, tracks := range [][]*domain.TrackDTO{primary, extra} {
		for _, track := range tracks {
			if track != nil {
				if _, ok := seen[track.ID]; !ok {
					seen[track.ID] = struct{}{}
					merged = append(merged, track)
				}
			}
		}
	}
	return merged
}

func collectTrackArtwork(track *domain.TrackDTO, keys map[string]struct{}) {
	if track.ArtworkKey != "" {
		keys[track.ArtworkKey] = struct{}{}
	}
	if track.Album != nil && track.Album.ArtworkKey != "" {
		keys[track.Album.ArtworkKey] = struct{}{}
	}
	for _, artist := range append(append([]*domain.Artist{}, track.Artists...), track.AlbumArtists...) {
		if artist == nil {
			continue
		}
		for _, key := range []*string{artist.ArtworkKeyManual, artist.ArtworkKeyLocal, artist.ArtworkKeyOnline} {
			if key != nil && *key != "" {
				keys[*key] = struct{}{}
			}
		}
	}
}

func (s *Service) addPlaylists(ctx context.Context, scope domain.MobileLibrarySyncScope, selected map[string]struct{}, favoriteTracks []*domain.TrackDTO, manifest *domain.MobileLibrarySyncManifest, artworkKeys map[string]struct{}) error {
	// Playlists are an explicit sync resource. A track selected through an
	// artist/album/genre must never pull in a playlist implicitly.
	favorites, err := s.playlists.GetByID(ctx, playlistapp.FavoritesPlaylistID)
	if err != nil {
		return fmt.Errorf("load favorites playlist: %w", err)
	}
	if favorites == nil {
		return fmt.Errorf("favorites playlist is unavailable")
	}
	members := make([]string, 0, len(favoriteTracks))
	for _, track := range favoriteTracks {
		if _, ok := selected[track.ID]; ok {
			members = append(members, track.ID)
		}
	}
	manifest.Playlists = append(manifest.Playlists, &domain.MobileSyncPlaylist{Playlist: favorites, TrackIDs: members})
	if favorites.ArtworkKey != nil && *favorites.ArtworkKey != "" {
		artworkKeys[*favorites.ArtworkKey] = struct{}{}
	}

	playlistIDs := map[string]struct{}{}
	switch scope.Kind {
	case domain.MobileLibrarySyncScopeAll:
		rows, err := s.playlists.GetAll(ctx)
		if err != nil {
			return fmt.Errorf("get playlists for all-library sync: %w", err)
		}
		for _, row := range rows {
			if row.ID != playlistapp.FavoritesPlaylistID {
				playlistIDs[row.ID] = struct{}{}
			}
		}
	case domain.MobileLibrarySyncScopePlaylists:
		for _, id := range scope.SelectedIDs {
			playlist, err := s.playlists.GetByID(ctx, id)
			if err != nil {
				return fmt.Errorf("load playlist for mobile sync: %w", err)
			}
			if playlist != nil && playlist.ID != playlistapp.FavoritesPlaylistID && !playlist.IsSmart {
				playlistIDs[id] = struct{}{}
			}
		}
	}
	ids := make([]string, 0, len(playlistIDs))
	for id := range playlistIDs {
		ids = append(ids, id)
	}
	sort.Strings(ids)
	for _, id := range ids {
		playlist, err := s.playlists.GetByID(ctx, id)
		if err != nil {
			return fmt.Errorf("load playlist: %w", err)
		}
		if playlist == nil {
			continue
		}
		rows, err := s.playlists.GetTracks(ctx, id)
		if err != nil {
			return fmt.Errorf("load playlist tracks: %w", err)
		}
		members := make([]string, 0, len(rows))
		for _, row := range rows {
			if _, ok := selected[row.ID]; ok {
				members = append(members, row.ID)
			}
		}
		manifest.Playlists = append(manifest.Playlists, &domain.MobileSyncPlaylist{Playlist: playlist, TrackIDs: members})
		if playlist.ArtworkKey != nil && *playlist.ArtworkKey != "" {
			artworkKeys[*playlist.ArtworkKey] = struct{}{}
		}
	}
	return nil
}

func fileAsset(ctx context.Context, id, kind, path string) (domain.MobileLibrarySyncAsset, error) {
	f, err := os.Open(path)
	if err != nil {
		return domain.MobileLibrarySyncAsset{}, err
	}
	defer func() { _ = f.Close() }()
	info, err := f.Stat()
	if err != nil {
		return domain.MobileLibrarySyncAsset{}, err
	}
	h := sha256.New()
	buf := make([]byte, 32<<10)
	for {
		if err := ctx.Err(); err != nil {
			return domain.MobileLibrarySyncAsset{}, err
		}
		n, readErr := f.Read(buf)
		if n > 0 {
			_, _ = h.Write(buf[:n])
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			return domain.MobileLibrarySyncAsset{}, readErr
		}
	}
	return domain.MobileLibrarySyncAsset{ID: id, Kind: kind, SHA256: hex.EncodeToString(h.Sum(nil)), Size: info.Size()}, nil
}

func (s *Service) ensureHTTPServer() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.server != nil {
		return nil
	}
	listener, err := net.Listen("tcp", "0.0.0.0:0")
	if err != nil {
		return fmt.Errorf("listen mobile sync HTTP server: %w", err)
	}
	s.port = listener.Addr().(*net.TCPAddr).Port
	s.server = &http.Server{Handler: http.HandlerFunc(s.serveHTTP), ReadHeaderTimeout: 10 * time.Second, WriteTimeout: 0, IdleTimeout: 2 * time.Minute}
	go func() {
		if err := s.server.Serve(listener); err != nil && err != http.ErrServerClosed {
			s.logger.Error("mobile library sync HTTP server stopped", "error", err)
		}
	}()
	return nil
}

func (s *Service) publishRequest(ctx context.Context, plan *domain.MobileLibrarySyncPlan, host string) error {
	if err := s.ensureHTTPServer(); err != nil {
		return err
	}
	identity, err := s.identity.Load(ctx)
	if err != nil {
		return fmt.Errorf("load pairing identity: %w", err)
	}
	if identity == nil {
		return fmt.Errorf("pairing identity unavailable")
	}
	s.mu.Lock()
	needsSubscription := !s.receiptsSubscribed
	s.mu.Unlock()
	if needsSubscription {
		if err := s.broker.Subscribe(ctx, "airmedy/library-sync/v1/"+identity.DeviceID+"/+/receipt", func(payload []byte) {
			if err := s.HandleReceipt(context.Background(), payload); err != nil {
				s.logger.Warn("reject mobile library sync receipt", "error", err)
			}
		}); err != nil {
			return fmt.Errorf("subscribe mobile library sync receipts: %w", err)
		}
		s.mu.Lock()
		s.receiptsSubscribed = true
		s.mu.Unlock()
	}
	key, ok, err := s.keys.Load(ctx)
	if err != nil {
		return fmt.Errorf("load pairing key: %w", err)
	}
	if !ok {
		return fmt.Errorf("pairing key unavailable")
	}
	request := syncRequest{Version: syncProtocolVersion, Type: syncRequestType, PlanID: plan.ID, DesktopID: identity.DeviceID, MobileID: plan.DeviceID, ManifestURL: fmt.Sprintf("http://%s:%d/mobile-sync/v1/plans/%s/manifest", host, s.port, plan.ID), ManifestHash: plan.ManifestHash, IssuedAt: time.Now().UTC().UnixMilli()}
	input, _ := json.Marshal(request)
	request.Signature = base64.RawURLEncoding.EncodeToString(ed25519.Sign(key, input))
	payload, _ := json.Marshal(request)
	if err := s.broker.Publish(ctx, syncRequestTopic(identity.DeviceID, plan.DeviceID), payload); err != nil {
		return fmt.Errorf("publish mobile library sync request: %w", err)
	}
	return nil
}

func syncRequestTopic(desktopID, mobileID string) string {
	return "airmedy/library-sync/v1/" + desktopID + "/" + mobileID + "/request"
}
func SyncReceiptTopic(desktopID, mobileID string) string {
	return "airmedy/library-sync/v1/" + desktopID + "/" + mobileID + "/receipt"
}

func playlistRequestTopic(desktopID, mobileID string) string {
	return "airmedy/playlist-sync/v1/" + desktopID + "/" + mobileID + "/request"
}

func (s *Service) ensureReconciliationSubscription(ctx context.Context, desktopID string) error {
	s.mu.Lock()
	alreadySubscribed := s.reconciliationSub
	s.mu.Unlock()
	if alreadySubscribed {
		return nil
	}
	if err := s.broker.Subscribe(ctx, "airmedy/playlist-sync/v1/"+desktopID+"/+/result", func(payload []byte) {
		if err := s.HandlePlaylistReconciliationResult(context.Background(), payload); err != nil {
			s.logger.Warn("reject playlist reconciliation result", "error", err)
		}
	}); err != nil {
		return fmt.Errorf("subscribe playlist reconciliation results: %w", err)
	}
	s.mu.Lock()
	s.reconciliationSub = true
	s.mu.Unlock()
	return nil
}

// HandlePlaylistReconciliationResult validates a mobile terminal result and
// wakes only the matching active reconciliation.
func (s *Service) HandlePlaylistReconciliationResult(ctx context.Context, payload []byte) error {
	var result playlistReconciliationResult
	if err := json.Unmarshal(payload, &result); err != nil {
		return fmt.Errorf("decode playlist reconciliation result: %w", err)
	}
	if result.Version != playlistSyncVersion || result.Type != playlistReconciliationResultType || result.ReconciliationID == "" || result.MobileID == "" {
		return fmt.Errorf("invalid playlist reconciliation result")
	}
	if skew := time.Since(time.UnixMilli(result.IssuedAt)); skew > 5*time.Minute || skew < -5*time.Minute {
		return fmt.Errorf("expired playlist reconciliation result")
	}
	device, err := s.devices.GetByDeviceID(ctx, result.MobileID)
	if err != nil {
		return fmt.Errorf("load reconciliation device: %w", err)
	}
	if device == nil {
		return fmt.Errorf("untrusted playlist reconciliation result")
	}
	signature, err := base64.RawURLEncoding.DecodeString(result.Signature)
	if err != nil || len(signature) != ed25519.SignatureSize {
		return fmt.Errorf("invalid playlist reconciliation signature")
	}
	result.Signature = ""
	input, _ := json.Marshal(result)
	if !ed25519.Verify(ed25519.PublicKey(device.PublicKey), input, signature) {
		return fmt.Errorf("invalid playlist reconciliation signature")
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	s.pruneReconciliationsLocked(time.Now().UTC())
	reconciliation := s.reconciliations[result.ReconciliationID]
	if reconciliation == nil || reconciliation.DeviceID != result.MobileID {
		return fmt.Errorf("unknown playlist reconciliation")
	}
	select {
	case reconciliation.result <- result:
	default:
	}
	return nil
}

func (s *Service) pruneReconciliationsLocked(now time.Time) {
	for id, reconciliation := range s.reconciliations {
		if !now.Before(reconciliation.Expires) {
			delete(s.reconciliations, id)
		}
	}
}

func (s *Service) cleanupPlaylistArtwork(ctx context.Context) error {
	if s.artwork == nil || s.tracks == nil || s.playlists == nil || s.artists == nil {
		return nil
	}
	active := make(map[string]bool)
	for _, source := range []func(context.Context) ([]string, error){s.tracks.GetAllArtworkKeys, s.playlists.GetAllArtworkKeys, s.artists.GetAllArtworkKeys} {
		keys, err := source(ctx)
		if err != nil {
			return err
		}
		for _, key := range keys {
			active[key] = true
		}
	}
	if s.staging != nil {
		if _, err := s.staging.DeleteExpired(ctx, time.Now().UTC()); err != nil {
			return err
		}
		keys, err := s.staging.ActiveArtworkKeys(ctx, time.Now().UTC())
		if err != nil {
			return err
		}
		for _, key := range keys {
			active[key] = true
		}
	}
	return s.artwork.CleanupOrphaned(ctx, active)
}

// HandleReceipt is called by the MQTT adapter after receiving a mobile message.
func (s *Service) HandleReceipt(ctx context.Context, payload []byte) error {
	var receipt syncReceipt
	if err := json.Unmarshal(payload, &receipt); err != nil {
		return fmt.Errorf("decode mobile sync receipt: %w", err)
	}
	if receipt.Version != syncProtocolVersion || receipt.Type != syncReceiptType || receipt.PlanID == "" || receipt.MobileID == "" || (receipt.AssetID == "" && !receipt.Complete) {
		return fmt.Errorf("invalid mobile sync receipt")
	}
	if skew := time.Since(time.UnixMilli(receipt.IssuedAt)); skew > 5*time.Minute || skew < -5*time.Minute {
		return fmt.Errorf("expired mobile sync receipt")
	}
	device, err := s.devices.GetByDeviceID(ctx, receipt.MobileID)
	if err != nil {
		return fmt.Errorf("load receipt device: %w", err)
	}
	if device == nil {
		return fmt.Errorf("untrusted mobile sync receipt")
	}
	signature, err := base64.RawURLEncoding.DecodeString(receipt.Signature)
	if err != nil {
		return fmt.Errorf("decode mobile sync receipt signature: %w", err)
	}
	receipt.Signature = ""
	input, _ := json.Marshal(receipt)
	if !ed25519.Verify(ed25519.PublicKey(device.PublicKey), input, signature) {
		return fmt.Errorf("invalid mobile sync receipt signature")
	}
	plan, err := s.plans.GetLatest(ctx, receipt.MobileID)
	if err != nil {
		return err
	}
	if plan == nil || plan.ID != receipt.PlanID || plan.Status != "active" {
		return fmt.Errorf("unknown mobile sync plan")
	}
	if receipt.Complete {
		if err := s.plans.MarkComplete(ctx, plan.ID, time.Now().UTC()); err != nil {
			return err
		}
	} else if _, err := s.plans.MarkReceipt(ctx, plan.ID, receipt.AssetID, time.Now().UTC()); err != nil {
		return err
	}
	updated, err := s.plans.GetLatest(ctx, receipt.MobileID)
	if err == nil && updated != nil {
		s.emit(updated)
	}
	return err
}

func (s *Service) serveHTTP(w http.ResponseWriter, r *http.Request) {
	if !s.authorizeHTTP(r) {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) >= 5 && parts[0] == "mobile-sync" && parts[1] == "v1" && parts[2] == "reconciliations" {
		s.serveReconciliationHTTP(w, r, parts)
		return
	}
	if len(parts) < 5 || parts[0] != "mobile-sync" || parts[1] != "v1" || parts[2] != "plans" {
		http.NotFound(w, r)
		return
	}
	planID := parts[3]
	plan, err := s.findPlan(r.Context(), planID)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	if parts[4] == "manifest" && len(parts) == 5 {
		body, err := marshalManifest(plan.Manifest)
		if err != nil {
			http.Error(w, "failed to encode manifest", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("ETag", plan.ManifestHash)
		_, _ = w.Write(body)
		return
	}
	if len(parts) == 6 && parts[4] == "assets" {
		s.serveAsset(w, r, plan, parts[5])
		return
	}
	http.NotFound(w, r)
}

func (s *Service) serveReconciliationHTTP(w http.ResponseWriter, r *http.Request, parts []string) {
	if len(parts) < 5 {
		http.NotFound(w, r)
		return
	}
	deviceID, _ := r.Context().Value(syncDeviceContextKey{}).(string)
	s.mu.Lock()
	s.pruneReconciliationsLocked(time.Now().UTC())
	reconciliation := s.reconciliations[parts[3]]
	s.mu.Unlock()
	if reconciliation == nil || reconciliation.DeviceID != deviceID {
		http.NotFound(w, r)
		return
	}
	if r.Method == http.MethodPost && len(parts) == 5 && parts[4] == "playlist-mutations" {
		s.servePlaylistMutations(w, r, reconciliation)
		return
	}
	if r.Method == http.MethodPost && len(parts) == 5 && parts[4] == "listening" {
		s.serveListeningSync(w, r, reconciliation)
		return
	}
	if r.Method == http.MethodPut && len(parts) == 6 && parts[4] == "playlist-artwork" {
		s.servePlaylistArtwork(w, r, reconciliation, parts[5])
		return
	}
	http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
}

func (s *Service) serveListeningSync(w http.ResponseWriter, r *http.Request, reconciliation *playlistReconciliation) {
	defer func() { _ = r.Body.Close() }()
	var snapshot domain.ListeningSyncSnapshot
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, maxReconciliationBodySize))
	if err != nil || json.Unmarshal(body, &snapshot) != nil || snapshot.Version != 1 || snapshot.ReconciliationID != reconciliation.ID {
		http.Error(w, "invalid listening snapshot", http.StatusBadRequest)
		return
	}
	if len(snapshot.Sessions)+len(snapshot.Attempts)+len(snapshot.DailyTracks)+len(snapshot.DailyAttempts) > 200000 {
		http.Error(w, "invalid listening snapshot", http.StatusBadRequest)
		return
	}
	// The desktop is the sync hub. A mobile may contribute only records it owns;
	// union rows learned from a previous desktop response are not trusted input.
	mobileID := reconciliation.DeviceID
	filtered := domain.ListeningSyncSnapshot{Version: 1, ReconciliationID: reconciliation.ID}
	for _, row := range snapshot.Sessions {
		if row.SourceDeviceID == mobileID {
			filtered.Sessions = append(filtered.Sessions, row)
		}
	}
	for _, row := range snapshot.Attempts {
		if row.SourceDeviceID == mobileID {
			filtered.Attempts = append(filtered.Attempts, row)
		}
	}
	for _, row := range snapshot.DailyTracks {
		if row.SourceDeviceID == mobileID {
			filtered.DailyTracks = append(filtered.DailyTracks, row)
		}
	}
	for _, row := range snapshot.DailyAttempts {
		if row.SourceDeviceID == mobileID {
			filtered.DailyAttempts = append(filtered.DailyAttempts, row)
		}
	}
	if !validListeningSnapshot(&filtered) {
		http.Error(w, "invalid listening snapshot", http.StatusBadRequest)
		return
	}
	if err := s.listening.ImportSnapshot(r.Context(), &filtered); err != nil {
		http.Error(w, "unable to import listening snapshot", http.StatusInternalServerError)
		return
	}
	merged, err := s.listening.ExportSnapshot(r.Context(), reconciliation.ID, time.Now().AddDate(0, 0, -180))
	if err != nil {
		http.Error(w, "unable to export listening snapshot", http.StatusInternalServerError)
		return
	}
	if len(merged.Sessions)+len(merged.Attempts)+len(merged.DailyTracks)+len(merged.DailyAttempts) > 200000 {
		http.Error(w, "listening snapshot is too large", http.StatusRequestEntityTooLarge)
		return
	}
	key, ok, err := s.keys.Load(r.Context())
	if err != nil || !ok {
		http.Error(w, "pairing key unavailable", http.StatusInternalServerError)
		return
	}
	merged.Signature = ""
	unsigned, err := json.Marshal(merged)
	if err != nil {
		http.Error(w, "unable to encode listening snapshot", http.StatusInternalServerError)
		return
	}
	merged.Signature = base64.RawURLEncoding.EncodeToString(ed25519.Sign(key, unsigned))
	body, err = json.Marshal(merged)
	if err != nil {
		http.Error(w, "unable to encode listening snapshot", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write(body)
}

func validListeningSnapshot(snapshot *domain.ListeningSyncSnapshot) bool {
	validReason := func(value string) bool { return value == "completed" || value == "skipped" || value == "stopped" }
	const maxCounter = 1_000_000_000
	latest := time.Now().Add(5 * time.Minute).UnixMilli()
	for _, row := range snapshot.Sessions {
		if row.ID == "" || len(row.ID) > 128 || row.SourceDeviceID == "" || row.TrackID == "" || len(row.TrackID) > 128 || row.StartedAt <= 0 || row.EndedAt < row.StartedAt || row.EndedAt > latest || row.ListenedSeconds < 0 || row.ListenedSeconds > maxCounter {
			return false
		}
	}
	for _, row := range snapshot.Attempts {
		if row.ID == "" || len(row.ID) > 128 || row.SourceDeviceID == "" || row.TrackID == "" || len(row.TrackID) > 128 || row.StartedAt <= 0 || row.EndedAt < row.StartedAt || row.EndedAt > latest || row.ListenedSeconds < 0 || row.ListenedSeconds > maxCounter || !validReason(row.EndReason) {
			return false
		}
	}
	for _, row := range snapshot.DailyTracks {
		if row.SourceDeviceID == "" || row.TrackID == "" || len(row.TrackID) > 128 || row.ListenedSeconds < 0 || row.ListenedSeconds > maxCounter || row.PlayCount < 0 || row.PlayCount > maxCounter {
			return false
		}
		if _, err := time.Parse("2006-01-02", row.LocalDate); err != nil {
			return false
		}
	}
	for _, row := range snapshot.DailyAttempts {
		if row.SourceDeviceID == "" || row.Attempts < 0 || row.Attempts > maxCounter || row.Completed < 0 || row.Completed > maxCounter || row.Skipped < 0 || row.Skipped > maxCounter || row.Stopped < 0 || row.Stopped > maxCounter || row.Completed+row.Skipped+row.Stopped > row.Attempts || row.ListenedSeconds < 0 || row.ListenedSeconds > maxCounter {
			return false
		}
		if _, err := time.Parse("2006-01-02", row.LocalDate); err != nil {
			return false
		}
	}
	return true
}

func (s *Service) servePlaylistArtwork(w http.ResponseWriter, r *http.Request, reconciliation *playlistReconciliation, expectedHash string) {
	claimedMIME := strings.TrimSpace(strings.Split(r.Header.Get("Content-Type"), ";")[0])
	if !validSHA256(expectedHash) || (claimedMIME != "image/jpeg" && claimedMIME != "image/png" && claimedMIME != "image/webp") {
		http.Error(w, "invalid artwork", http.StatusBadRequest)
		return
	}
	defer func() { _ = r.Body.Close() }()
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, 10<<20))
	if err != nil || hashBody(body) != expectedHash || http.DetectContentType(body) != claimedMIME {
		http.Error(w, "invalid artwork", http.StatusBadRequest)
		return
	}
	key, err := s.artwork.Save(r.Context(), body, claimedMIME)
	if err != nil {
		http.Error(w, "invalid artwork", http.StatusBadRequest)
		return
	}
	if s.staging != nil {
		if err := s.staging.Save(r.Context(), domain.PlaylistArtworkStaging{ReconciliationID: reconciliation.ID, DeviceID: reconciliation.DeviceID, SHA256: expectedHash, ArtworkKey: key, ExpiresAt: reconciliation.Expires}); err != nil {
			http.Error(w, "unable to stage artwork", http.StatusInternalServerError)
			return
		}
	}
	s.mu.Lock()
	reconciliation.artwork[expectedHash] = key
	s.mu.Unlock()
	w.Header().Set("Content-Type", "application/json")
	_, _ = w.Write([]byte(`{"sha256":"` + expectedHash + `"}`))
}

func (s *Service) servePlaylistMutations(w http.ResponseWriter, r *http.Request, reconciliation *playlistReconciliation) {
	defer func() { _ = r.Body.Close() }()
	body, err := io.ReadAll(http.MaxBytesReader(w, r.Body, 512<<10))
	if err != nil {
		http.Error(w, "invalid batch", http.StatusBadRequest)
		return
	}
	var batch playlistMutationBatch
	if err := json.Unmarshal(body, &batch); err != nil || batch.Version != playlistSyncVersion || batch.ReconciliationID != reconciliation.ID {
		http.Error(w, "invalid batch", http.StatusBadRequest)
		return
	}
	deviceID, _ := r.Context().Value(syncDeviceContextKey{}).(string)
	s.mu.Lock()
	uploadedArtwork := make(map[string]string, len(reconciliation.artwork))
	for hash, key := range reconciliation.artwork {
		uploadedArtwork[hash] = key
	}
	s.mu.Unlock()
	result := playlistMutationBatchResult{Version: playlistSyncVersion, ReconciliationID: batch.ReconciliationID, Results: make([]playlistMutationResult, 0, len(batch.Mutations))}
	for _, mutation := range batch.Mutations {
		status := s.applyPlaylistMutation(r.Context(), deviceID, reconciliation.Scope, mutation, uploadedArtwork)
		result.Results = append(result.Results, playlistMutationResult{MutationID: mutation.MutationID, Status: status})
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(result)
}

func (s *Service) findPlan(ctx context.Context, planID string) (*domain.MobileLibrarySyncPlan, error) {
	// The mobile ID is authenticated in authorizeHTTP and set on the request.
	deviceID, _ := ctx.Value(syncDeviceContextKey{}).(string)
	plan, err := s.plans.GetLatest(ctx, deviceID)
	if err != nil || plan == nil || plan.ID != planID || plan.Status != "active" {
		return nil, fmt.Errorf("plan unavailable")
	}
	return plan, nil
}

func (s *Service) serveAsset(w http.ResponseWriter, r *http.Request, plan *domain.MobileLibrarySyncPlan, assetID string) {
	var asset *domain.MobileLibrarySyncAsset
	for i := range plan.Manifest.Assets {
		if plan.Manifest.Assets[i].ID == assetID {
			asset = &plan.Manifest.Assets[i]
			break
		}
	}
	if asset == nil {
		http.NotFound(w, r)
		return
	}
	var path string
	if strings.HasPrefix(asset.ID, "audio:") {
		track, err := s.tracks.GetByID(r.Context(), strings.TrimPrefix(asset.ID, "audio:"))
		if err != nil || track == nil {
			http.NotFound(w, r)
			return
		}
		path = track.Path
	} else if strings.HasPrefix(asset.ID, "artwork:") {
		path = s.artwork.GetPath(strings.TrimPrefix(asset.ID, "artwork:"))
	} else {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("ETag", asset.SHA256)
	w.Header().Set("X-Airmedy-SHA256", asset.SHA256)
	http.ServeFile(w, r, path)
}

type syncDeviceContextKey struct{}

func (s *Service) authorizeHTTP(r *http.Request) bool {
	deviceID, timestamp, nonce, signatureText := r.Header.Get("X-Airmedy-Mobile-ID"), r.Header.Get("X-Airmedy-Timestamp"), r.Header.Get("X-Airmedy-Nonce"), r.Header.Get("X-Airmedy-Signature")
	if deviceID == "" || timestamp == "" || nonce == "" || signatureText == "" {
		return false
	}
	issued, err := time.Parse(time.RFC3339Nano, timestamp)
	if err != nil || time.Since(issued) > 5*time.Minute || time.Until(issued) > 5*time.Minute {
		return false
	}
	device, err := s.devices.GetByDeviceID(r.Context(), deviceID)
	if err != nil || device == nil {
		return false
	}
	signature, err := base64.RawURLEncoding.DecodeString(signatureText)
	if err != nil {
		return false
	}
	body, err := io.ReadAll(io.LimitReader(r.Body, maxReconciliationBodySize+1))
	if err != nil || len(body) > maxReconciliationBodySize {
		return false
	}
	r.Body = io.NopCloser(bytes.NewReader(body))
	input := []byte(strings.Join([]string{r.Method, r.URL.EscapedPath(), hashBody(body), timestamp, nonce}, "\n"))
	if !ed25519.Verify(ed25519.PublicKey(device.PublicKey), input, signature) {
		return false
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	for key, expiry := range s.nonces {
		if now.After(expiry) {
			delete(s.nonces, key)
		}
	}
	key := deviceID + ":" + nonce
	if _, used := s.nonces[key]; used {
		return false
	}
	s.nonces[key] = now.Add(5 * time.Minute)
	*r = *r.WithContext(context.WithValue(r.Context(), syncDeviceContextKey{}, deviceID))
	return true
}
