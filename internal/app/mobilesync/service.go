package mobilesync

import (
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

type Service struct {
	plans      domain.MobileLibrarySyncPlanRepository
	tracks     domain.TrackRepository
	playlists  domain.PlaylistRepository
	lyrics     *lyricsapp.LyricsService
	lyricCache domain.MobileSyncLyricCacheRepository
	analysis   domain.AnalysisRepository
	artwork    domain.ArtworkCache
	devices    domain.TrustedMobileDeviceRepository
	identity   domain.PairingIdentityRepository
	keys       domain.PairingKeyStore
	broker     domain.PairingBroker
	logger     *slog.Logger

	mu                 sync.Mutex
	server             *http.Server
	port               int
	nonces             map[string]time.Time
	receiptsSubscribed bool
	listeners          []func(*domain.MobileLibrarySyncPlan)
}

func NewService(plans domain.MobileLibrarySyncPlanRepository, tracks domain.TrackRepository, playlists domain.PlaylistRepository, lyrics *lyricsapp.LyricsService, lyricCache domain.MobileSyncLyricCacheRepository, analysis domain.AnalysisRepository, artwork domain.ArtworkCache, devices domain.TrustedMobileDeviceRepository, identity domain.PairingIdentityRepository, keys domain.PairingKeyStore, broker domain.PairingBroker, logger *slog.Logger) *Service {
	return &Service{plans: plans, tracks: tracks, playlists: playlists, lyrics: lyrics, lyricCache: lyricCache, analysis: analysis, artwork: artwork, devices: devices, identity: identity, keys: keys, broker: broker, logger: logger, nonces: make(map[string]time.Time)}
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

// Start starts a new immutable snapshot, or re-announces the active snapshot
// when the user presses Sync again with unchanged scope.
func (s *Service) Start(ctx context.Context, deviceID string, scope domain.MobileLibrarySyncScope, host string, replace bool) (*domain.MobileLibrarySyncPlan, error) {
	if _, err := uuid.Parse(deviceID); err != nil {
		return nil, fmt.Errorf("invalid mobile device ID")
	}
	if strings.TrimSpace(host) == "" || net.ParseIP(host) == nil {
		return nil, fmt.Errorf("invalid desktop sync address")
	}
	trusted, err := s.devices.GetByDeviceID(ctx, deviceID)
	if err != nil {
		return nil, fmt.Errorf("load trusted mobile device: %w", err)
	}
	if trusted == nil {
		return nil, fmt.Errorf("mobile device is not trusted")
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
	tracks, err := s.resolveScope(ctx, &scope)
	if err != nil {
		return nil, err
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
		asset, err := fileAsset("audio:"+dto.ID, "audio", dto.Path)
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
	if err := s.addPlaylists(ctx, selected, &manifest, artworkKeys); err != nil {
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
		asset, err := fileAsset("artwork:"+key, "artwork", s.artwork.GetPath(key))
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
	scope.Kind = strings.ToLower(strings.TrimSpace(scope.Kind))
	sort.Strings(scope.SelectedIDs)
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
	seen := make(map[string]struct{}, len(result))
	deduped := make([]*domain.TrackDTO, 0, len(result))
	for _, track := range result {
		if _, ok := seen[track.ID]; !ok {
			seen[track.ID] = struct{}{}
			deduped = append(deduped, track)
		}
	}
	return deduped, nil
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

func (s *Service) addPlaylists(ctx context.Context, selected map[string]struct{}, manifest *domain.MobileLibrarySyncManifest, artworkKeys map[string]struct{}) error {
	playlistIDs := map[string]struct{}{}
	for trackID := range selected {
		ids, err := s.playlists.GetPlaylistsForTrack(ctx, trackID)
		if err != nil {
			return fmt.Errorf("get playlists for track: %w", err)
		}
		for _, id := range ids {
			playlistIDs[id] = struct{}{}
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
		if len(members) == 0 {
			continue
		}
		manifest.Playlists = append(manifest.Playlists, &domain.MobileSyncPlaylist{Playlist: playlist, TrackIDs: members})
		if playlist.ArtworkKey != nil && *playlist.ArtworkKey != "" {
			artworkKeys[*playlist.ArtworkKey] = struct{}{}
		}
	}
	return nil
}

func fileAsset(id, kind, path string) (domain.MobileLibrarySyncAsset, error) {
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
	if _, err := io.Copy(h, f); err != nil {
		return domain.MobileLibrarySyncAsset{}, err
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
	if r.Method != http.MethodGet || !s.authorizeHTTP(r) {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
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
	input := []byte(strings.Join([]string{r.Method, r.URL.EscapedPath(), timestamp, nonce}, "\n"))
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
