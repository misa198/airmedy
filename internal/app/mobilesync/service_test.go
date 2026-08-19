package mobilesync

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"errors"
	"image"
	"image/color"
	"image/png"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"airmedy/internal/domain"
	"airmedy/internal/infra/sqlite"
	"github.com/stretchr/testify/require"
)

type testArtworkCache struct{ saved int }

func (c *testArtworkCache) Save(context.Context, []byte, string) (string, error) {
	c.saved++
	return "artwork.png", nil
}
func (*testArtworkCache) GetPath(string) string                                  { return "" }
func (*testArtworkCache) GetVariantPath(string, string) string                   { return "" }
func (*testArtworkCache) Exists(string) bool                                     { return false }
func (*testArtworkCache) CleanupOrphaned(context.Context, map[string]bool) error { return nil }

type testStagingRepo struct {
	saved *domain.PlaylistArtworkStaging
}

func (r *testStagingRepo) Save(_ context.Context, e domain.PlaylistArtworkStaging) error {
	r.saved = &e
	return nil
}
func (*testStagingRepo) Get(context.Context, string, string, string) (*domain.PlaylistArtworkStaging, error) {
	return nil, nil
}
func (*testStagingRepo) DeleteExpired(context.Context, time.Time) ([]string, error) { return nil, nil }
func (*testStagingRepo) DeleteReconciliation(context.Context, string, string) ([]string, error) {
	return nil, nil
}
func (*testStagingRepo) ActiveArtworkKeys(context.Context, time.Time) ([]string, error) {
	return nil, nil
}

type testDeviceRepo struct{ device *domain.TrustedMobileDevice }

func (r testDeviceRepo) List(context.Context) ([]*domain.TrustedMobileDevice, error) { return nil, nil }
func (r testDeviceRepo) GetByDeviceID(_ context.Context, id string) (*domain.TrustedMobileDevice, error) {
	if r.device != nil && r.device.DeviceID == id {
		return r.device, nil
	}
	return nil, nil
}
func (testDeviceRepo) Save(context.Context, *domain.TrustedMobileDevice) error { return nil }
func (testDeviceRepo) Touch(context.Context, string, time.Time) error          { return nil }
func (testDeviceRepo) Delete(context.Context, string) error                    { return nil }

func signedRequest(t *testing.T, key ed25519.PrivateKey, method, target, deviceID, nonce string, body []byte, at time.Time) *http.Request {
	t.Helper()
	req := httptest.NewRequest(method, target, bytes.NewReader(body))
	timestamp := at.UTC().Format(time.RFC3339Nano)
	input := strings.Join([]string{method, req.URL.EscapedPath(), hashBody(body), timestamp, nonce}, "\n")
	req.Header.Set("X-Airmedy-Mobile-ID", deviceID)
	req.Header.Set("X-Airmedy-Timestamp", timestamp)
	req.Header.Set("X-Airmedy-Nonce", nonce)
	req.Header.Set("X-Airmedy-Signature", base64.RawURLEncoding.EncodeToString(ed25519.Sign(key, []byte(input))))
	return req
}

func TestAuthorizeHTTPBindsMethodEscapedPathBodyAndRejectsReplay(t *testing.T) {
	pub, private, err := ed25519.GenerateKey(rand.Reader)
	require.NoError(t, err)
	deviceID := "11111111-1111-4111-8111-111111111111"
	svc := &Service{devices: testDeviceRepo{&domain.TrustedMobileDevice{DeviceID: deviceID, PublicKey: pub}}, nonces: map[string]time.Time{}}
	body := []byte(`{"name":"Airmedy"}`)
	req := signedRequest(t, private, http.MethodPost, "http://desktop/mobile-sync/v1/reconciliations/a/playlist%2Dmutations", deviceID, "nonce", body, time.Now())
	require.True(t, svc.authorizeHTTP(req))
	restored, err := io.ReadAll(req.Body)
	require.NoError(t, err)
	require.Equal(t, body, restored)
	require.False(t, svc.authorizeHTTP(signedRequest(t, private, http.MethodPost, req.URL.String(), deviceID, "nonce", body, time.Now())), "nonce replay")
	wrongMethod := signedRequest(t, private, http.MethodPost, req.URL.String(), deviceID, "method", body, time.Now())
	wrongMethod.Method = http.MethodPut
	require.False(t, svc.authorizeHTTP(wrongMethod))
	tampered := signedRequest(t, private, http.MethodPost, req.URL.String(), deviceID, "body", body, time.Now())
	tampered.Body = io.NopCloser(strings.NewReader("different"))
	require.False(t, svc.authorizeHTTP(tampered))
	expired := signedRequest(t, private, http.MethodPost, req.URL.String(), deviceID, "expired", body, time.Now().Add(-6*time.Minute))
	require.False(t, svc.authorizeHTTP(expired))
}

func TestListeningSnapshotSigningShapeMatchesMobile(t *testing.T) {
	encoded, err := json.Marshal(domain.ListeningSyncSnapshot{Version: 1, ReconciliationID: "r", Sessions: []domain.ListeningSyncSession{}, Attempts: []domain.ListeningSyncAttempt{}, DailyTracks: []domain.DailyTrackListeningStat{}, DailyAttempts: []domain.DailyPlaybackAttemptStat{}})
	require.NoError(t, err)
	require.Equal(t, `{"version":1,"reconciliation_id":"r","sessions":[],"attempts":[],"daily_tracks":[],"daily_attempts":[],"signature":""}`, string(encoded))
}

func TestPlaylistMutationTerminalStatusesAndDurableLedger(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "library.db")
	db, err := sqlite.NewDB(dbPath, slog.Default())
	require.NoError(t, err)
	deviceID := "11111111-1111-4111-8111-111111111111"
	_, err = db.Exec(`INSERT INTO paired_mobile_devices (device_id, public_key, display_name, platform, paired_at, last_seen_at) VALUES (?, ?, 'Phone', 'Android', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)`, deviceID, make([]byte, 32))
	require.NoError(t, err)
	repo := sqlite.NewPlaylistRepository(db)
	svc := &Service{playlists: repo, ledger: sqlite.NewPlaylistMutationLedger(db), lww: sqlite.NewPlaylistMutationLWW(db), tx: sqlite.NewTxManager(db), logger: slog.Default()}
	all := domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAll}
	create := playlistMutation{MutationID: "00000000-0000-4000-8000-000000000001", PlaylistID: "playlist", Operation: "CREATE", UpdatedAt: 100, Payload: playlistMutationPayload{Name: "Playlist"}}
	require.Equal(t, "applied", svc.applyPlaylistMutation(context.Background(), deviceID, all, create, nil))
	require.Equal(t, "duplicate", svc.applyPlaylistMutation(context.Background(), deviceID, all, create, nil))
	require.Equal(t, "stale", svc.applyPlaylistMutation(context.Background(), deviceID, all, createWith(create, "00000000-0000-4000-8000-000000000002", 99, "UPDATE"), nil))
	scope := domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopePlaylists, SelectedIDs: []string{"other"}}
	require.Equal(t, "scope-conflict", svc.applyPlaylistMutation(context.Background(), deviceID, scope, createWith(create, "00000000-0000-4000-8000-000000000003", 101, "UPDATE"), nil))
	require.Equal(t, "rejected", svc.applyPlaylistMutation(context.Background(), deviceID, all, createWith(create, "00000000-0000-4000-8000-000000000004", 102, "UNKNOWN"), nil))
	concurrent := createWith(create, "00000000-0000-4000-8000-000000000005", 103, "CREATE")
	concurrent.PlaylistID = "concurrent"
	statuses := make(chan string, 2)
	for range 2 {
		go func() { statuses <- svc.applyPlaylistMutation(context.Background(), deviceID, all, concurrent, nil) }()
	}
	got := []string{<-statuses, <-statuses}
	require.ElementsMatch(t, []string{"applied", "duplicate"}, got)
	require.NoError(t, db.Close())

	db, err = sqlite.NewDB(dbPath, slog.Default())
	require.NoError(t, err)
	t.Cleanup(func() { require.NoError(t, db.Close()) })
	entry, err := sqlite.NewPlaylistMutationLedger(db).Get(context.Background(), deviceID, create.MutationID)
	require.NoError(t, err)
	require.NotNil(t, entry)
	require.Equal(t, "applied", entry.Result)
}

func TestFavoriteArtworkMutationIsApplied(t *testing.T) {
	db, err := sqlite.NewDB(filepath.Join(t.TempDir(), "library.db"), slog.Default())
	require.NoError(t, err)
	t.Cleanup(func() { require.NoError(t, db.Close()) })
	repo := sqlite.NewPlaylistRepository(db)
	require.NoError(t, repo.Save(context.Background(), &domain.Playlist{ID: "favorites", Name: "Favorites"}))
	svc := &Service{playlists: repo}
	mutation := playlistMutation{PlaylistID: "favorites", Operation: "SET_ARTWORK", Payload: playlistMutationPayload{ArtworkSHA256: strings.Repeat("a", 64)}}

	require.Equal(t, "applied", svc.applyNewPlaylistMutation(context.Background(), domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAll}, mutation, map[string]string{mutation.Payload.ArtworkSHA256: "artwork-key"}))
	playlist, err := repo.GetByID(context.Background(), "favorites")
	require.NoError(t, err)
	require.Equal(t, "artwork-key", *playlist.ArtworkKey)
}

func TestAddPlaylistsIncludesEmptyPlaylists(t *testing.T) {
	db, err := sqlite.NewDB(filepath.Join(t.TempDir(), "library.db"), slog.Default())
	require.NoError(t, err)
	defer func() { require.NoError(t, db.Close()) }()
	repo := sqlite.NewPlaylistRepository(db)
	require.NoError(t, repo.Save(context.Background(), &domain.Playlist{ID: "empty", Name: "Empty"}))

	manifest := domain.MobileLibrarySyncManifest{}
	svc := &Service{playlists: repo}
	err = svc.addPlaylists(context.Background(), domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAll}, map[string]struct{}{}, &manifest, map[string]struct{}{})
	require.NoError(t, err)
	require.Len(t, manifest.Playlists, 1)
	require.Equal(t, "empty", manifest.Playlists[0].Playlist.ID)
	require.Empty(t, manifest.Playlists[0].TrackIDs)
}

func TestPlaylistArtworkValidatesMimeHashSizeAndOwnership(t *testing.T) {
	var body bytes.Buffer
	img := image.NewRGBA(image.Rect(0, 0, 1, 1))
	img.Set(0, 0, color.RGBA{R: 0xe1, G: 0x1d, B: 0x48, A: 0xff})
	require.NoError(t, png.Encode(&body, img))
	data := body.Bytes()
	hash := hashBody(data)
	cache := &testArtworkCache{}
	staging := &testStagingRepo{}
	svc := &Service{artwork: cache, staging: staging}
	reconciliation := &playlistReconciliation{ID: "reconciliation", DeviceID: "mobile", Expires: time.Now().Add(time.Minute), artwork: map[string]string{}}
	upload := func(contentType, expected string, payload []byte) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPut, "/", bytes.NewReader(payload))
		req.Header.Set("Content-Type", contentType)
		response := httptest.NewRecorder()
		svc.servePlaylistArtwork(response, req, reconciliation, expected)
		return response
	}
	require.Equal(t, http.StatusOK, upload("image/png", hash, data).Code)
	require.Equal(t, 1, cache.saved)
	require.NotNil(t, staging.saved)
	require.Equal(t, "reconciliation", staging.saved.ReconciliationID)
	require.Equal(t, "mobile", staging.saved.DeviceID)
	require.Equal(t, http.StatusBadRequest, upload("image/jpeg", hash, data).Code)
	require.Equal(t, http.StatusBadRequest, upload("image/png", strings.Repeat("0", 64), data).Code)
	require.Equal(t, http.StatusBadRequest, upload("image/png", hash, make([]byte, (10<<20)+1)).Code)
}

func createWith(base playlistMutation, id string, at int64, operation string) playlistMutation {
	base.MutationID = id
	base.UpdatedAt = at
	base.Operation = operation
	return base
}

type testIdentityRepo struct{ identity *domain.PairingIdentity }

func (r testIdentityRepo) Load(context.Context) (*domain.PairingIdentity, error) {
	return r.identity, nil
}
func (testIdentityRepo) Save(context.Context, *domain.PairingIdentity) error { return nil }

type testKeyStore struct{ key ed25519.PrivateKey }

func (s testKeyStore) Load(context.Context) (ed25519.PrivateKey, bool, error) {
	return s.key, true, nil
}
func (testKeyStore) Save(context.Context, ed25519.PrivateKey) error { return nil }

type testBroker struct {
	publishErr error
	payload    []byte
	published  chan struct{}
}

func (*testBroker) Start(context.Context, string, int, func([]byte), func(string, bool)) (int, error) {
	return 0, nil
}
func (*testBroker) Stop(context.Context) error               { return nil }
func (*testBroker) Disconnect(context.Context, string) error { return nil }
func (b *testBroker) Publish(_ context.Context, _ string, payload []byte) error {
	b.payload = payload
	if b.published != nil {
		select {
		case b.published <- struct{}{}:
		default:
		}
	}
	return b.publishErr
}
func (*testBroker) Subscribe(context.Context, string, func([]byte)) error { return nil }
func (*testBroker) Running() bool                                         { return true }

type testPlanRepo struct {
	saves      int
	plan       *domain.MobileLibrarySyncPlan
	superseded int
}

func (r *testPlanRepo) GetLatest(context.Context, string) (*domain.MobileLibrarySyncPlan, error) {
	return r.plan, nil
}
func (r *testPlanRepo) Save(context.Context, *domain.MobileLibrarySyncPlan) error {
	r.saves++
	return nil
}
func (r *testPlanRepo) MarkSuperseded(context.Context, string) error {
	r.superseded++
	r.plan.Status = "superseded"
	return nil
}
func (r *testPlanRepo) MarkAllActiveSuperseded(context.Context) error {
	if r.plan != nil && r.plan.Status == "active" {
		r.plan.Status = "superseded"
		r.superseded++
	}
	return nil
}
func (*testPlanRepo) MarkReceipt(context.Context, string, string, time.Time) (int, error) {
	return 0, nil
}
func (*testPlanRepo) MarkComplete(context.Context, string, time.Time) error { return nil }

func TestCancelMakesTheActivePlanUnavailableAndNotifiesDesktop(t *testing.T) {
	deviceID := "11111111-1111-4111-8111-111111111111"
	plans := &testPlanRepo{plan: &domain.MobileLibrarySyncPlan{ID: "plan", DeviceID: deviceID, Status: "active"}}
	svc := &Service{plans: plans}
	var updated *domain.MobileLibrarySyncPlan
	svc.AddListener(func(plan *domain.MobileLibrarySyncPlan) { updated = plan })

	plan, err := svc.Cancel(context.Background(), deviceID)

	require.NoError(t, err)
	require.Equal(t, "superseded", plan.Status)
	require.Equal(t, 1, plans.superseded)
	require.Same(t, plan, updated)
}

func TestOnStartSupersedesPlanInterruptedByDesktopRestart(t *testing.T) {
	plans := &testPlanRepo{plan: &domain.MobileLibrarySyncPlan{Status: "active"}}
	svc := &Service{plans: plans}

	require.NoError(t, svc.OnStart(context.Background()))
	require.Equal(t, "superseded", plans.plan.Status)
}

func TestCancelReturnsAnAlreadyCompletedPlan(t *testing.T) {
	deviceID := "11111111-1111-4111-8111-111111111111"
	plans := &testPlanRepo{plan: &domain.MobileLibrarySyncPlan{ID: "plan", DeviceID: deviceID, Status: "complete"}}

	plan, err := (&Service{plans: plans}).Cancel(context.Background(), deviceID)

	require.NoError(t, err)
	require.Equal(t, "complete", plan.Status)
	require.Zero(t, plans.superseded)
}

func TestCancelIfActiveLeavesTerminalPlanUnchanged(t *testing.T) {
	deviceID := "11111111-1111-4111-8111-111111111111"
	plans := &testPlanRepo{plan: &domain.MobileLibrarySyncPlan{ID: "plan", DeviceID: deviceID, Status: "complete"}}

	err := (&Service{plans: plans}).CancelIfActive(context.Background(), deviceID)

	require.NoError(t, err)
	require.Zero(t, plans.superseded)
}

func TestCancelIfActiveSupersedesActivePlan(t *testing.T) {
	deviceID := "11111111-1111-4111-8111-111111111111"
	plans := &testPlanRepo{plan: &domain.MobileLibrarySyncPlan{ID: "plan", DeviceID: deviceID, Status: "active"}}

	err := (&Service{plans: plans}).CancelIfActive(context.Background(), deviceID)

	require.NoError(t, err)
	require.Equal(t, "superseded", plans.plan.Status)
}

func TestCancelIfActiveCancelsPlanPreparation(t *testing.T) {
	deviceID := "11111111-1111-4111-8111-111111111111"
	_, desktopKey, err := ed25519.GenerateKey(rand.Reader)
	require.NoError(t, err)
	broker := &testBroker{published: make(chan struct{}, 1)}
	plans := &testPlanRepo{}
	svc := &Service{
		plans:           plans,
		devices:         testDeviceRepo{&domain.TrustedMobileDevice{DeviceID: deviceID}},
		identity:        testIdentityRepo{&domain.PairingIdentity{DeviceID: "desktop"}},
		keys:            testKeyStore{desktopKey},
		broker:          broker,
		server:          &http.Server{},
		reconciliations: map[string]*playlistReconciliation{},
	}
	result := make(chan error, 1)
	go func() {
		_, err := svc.Start(context.Background(), deviceID, domain.MobileLibrarySyncScope{Kind: "all"}, "127.0.0.1", false)
		result <- err
	}()
	select {
	case <-broker.published:
	case <-time.After(time.Second):
		t.Fatal("plan preparation did not start")
	}

	require.NoError(t, svc.CancelIfActive(context.Background(), deviceID))
	require.ErrorIs(t, <-result, context.Canceled)
	require.Zero(t, plans.saves)
}

func TestReconciliationOfflineAndTimeoutReturnWithoutPlanWork(t *testing.T) {
	_, desktopKey, _ := ed25519.GenerateKey(rand.Reader)
	identity := testIdentityRepo{&domain.PairingIdentity{DeviceID: "desktop"}}
	t.Run("offline", func(t *testing.T) {
		broker := &testBroker{publishErr: errors.New("offline")}
		svc := &Service{identity: identity, keys: testKeyStore{desktopKey}, broker: broker, reconciliations: map[string]*playlistReconciliation{}}
		err := svc.reconcilePlaylists(context.Background(), "mobile", domain.MobileLibrarySyncScope{Kind: "all"}, "127.0.0.1")
		require.ErrorContains(t, err, "publish playlist reconciliation request")
	})
	t.Run("start does not create a plan when reconciliation fails", func(t *testing.T) {
		deviceID := "11111111-1111-4111-8111-111111111111"
		plans := &testPlanRepo{}
		svc := &Service{
			plans:           plans,
			devices:         testDeviceRepo{&domain.TrustedMobileDevice{DeviceID: deviceID}},
			identity:        identity,
			keys:            testKeyStore{desktopKey},
			broker:          &testBroker{publishErr: errors.New("offline")},
			server:          &http.Server{},
			reconciliations: map[string]*playlistReconciliation{},
		}
		plan, err := svc.Start(context.Background(), deviceID, domain.MobileLibrarySyncScope{Kind: "all"}, "127.0.0.1", false)
		require.Nil(t, plan)
		require.ErrorContains(t, err, "publish playlist reconciliation request")
		require.Zero(t, plans.saves)
	})
	t.Run("timeout", func(t *testing.T) {
		old := reconciliationTimeout
		reconciliationTimeout = 10 * time.Millisecond
		defer func() { reconciliationTimeout = old }()
		broker := &testBroker{}
		svc := &Service{identity: identity, keys: testKeyStore{desktopKey}, broker: broker, reconciliations: map[string]*playlistReconciliation{}}
		err := svc.reconcilePlaylists(context.Background(), "mobile", domain.MobileLibrarySyncScope{Kind: "all"}, "127.0.0.1")
		require.ErrorContains(t, err, "timed out")
		var request playlistReconciliationRequest
		require.NoError(t, json.Unmarshal(broker.payload, &request))
		require.Equal(t, "mobile", request.MobileID)
	})
}

func TestReconciliationResultRequiresMatchingIdentityTimestampAndSignature(t *testing.T) {
	pub, mobileKey, err := ed25519.GenerateKey(rand.Reader)
	require.NoError(t, err)
	mobileID := "11111111-1111-4111-8111-111111111111"
	reconciliationID := "22222222-2222-4222-8222-222222222222"
	svc := &Service{
		devices:         testDeviceRepo{&domain.TrustedMobileDevice{DeviceID: mobileID, PublicKey: pub}},
		reconciliations: map[string]*playlistReconciliation{reconciliationID: {ID: reconciliationID, DeviceID: mobileID, Expires: time.Now().Add(time.Minute), result: make(chan playlistReconciliationResult, 1)}},
	}
	sign := func(value playlistReconciliationResult) []byte {
		value.Signature = ""
		input, marshalErr := json.Marshal(value)
		require.NoError(t, marshalErr)
		value.Signature = base64.RawURLEncoding.EncodeToString(ed25519.Sign(mobileKey, input))
		payload, marshalErr := json.Marshal(value)
		require.NoError(t, marshalErr)
		return payload
	}
	valid := playlistReconciliationResult{Version: playlistSyncVersion, Type: playlistReconciliationResultType, ReconciliationID: reconciliationID, MobileID: mobileID, IssuedAt: time.Now().UnixMilli()}
	require.NoError(t, svc.HandlePlaylistReconciliationResult(context.Background(), sign(valid)))
	require.Len(t, svc.reconciliations[reconciliationID].result, 1)
	badIdentity := valid
	badIdentity.MobileID = "33333333-3333-4333-8333-333333333333"
	require.Error(t, svc.HandlePlaylistReconciliationResult(context.Background(), sign(badIdentity)))
	expired := valid
	expired.IssuedAt = time.Now().Add(-6 * time.Minute).UnixMilli()
	require.ErrorContains(t, svc.HandlePlaylistReconciliationResult(context.Background(), sign(expired)), "expired")
	badSignature := valid
	payload := sign(badSignature)
	payload[len(payload)-2] ^= 1
	require.Error(t, svc.HandlePlaylistReconciliationResult(context.Background(), payload))
}

func TestSameScopeIgnoresSelectionOrder(t *testing.T) {
	require.True(t, sameScope(
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeArtists, SelectedIDs: []string{"b", "a"}},
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeArtists, SelectedIDs: []string{"a", "b"}},
	))
	require.False(t, sameScope(
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeArtists, SelectedIDs: []string{"a"}},
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAlbums, SelectedIDs: []string{"a"}},
	))
}

func TestMarshalManifestProducesCompactWireBytes(t *testing.T) {
	manifest := domain.MobileLibrarySyncManifest{
		Version:  1,
		PlanID:   "plan-1",
		Revision: "a",
		Scope:    domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAll, SelectedIDs: []string{}},
		Lyrics:   map[string]*domain.Lyric{},
		Analysis: map[string]*domain.TrackFeatures{},
		Assets:   []domain.MobileLibrarySyncAsset{},
	}

	body, err := marshalManifest(manifest)
	require.NoError(t, err)
	require.NotEqual(t, byte('\n'), body[len(body)-1], "manifest wire bytes must not have Encoder's trailing newline")
}

func TestFileAssetIncludesStableContentHash(t *testing.T) {
	path := filepath.Join(t.TempDir(), "track.mp3")
	require.NoError(t, os.WriteFile(path, []byte("airmedy"), 0o600))
	asset, err := fileAsset(context.Background(), "audio:track-1", "audio", path)
	require.NoError(t, err)
	require.Equal(t, "audio:track-1", asset.ID)
	require.Equal(t, int64(7), asset.Size)
	require.Equal(t, "0f86571fd055a92ddb32478158a63e87ee84883e1775acb56adbb5ef4bdbc5dc", asset.SHA256)
}

func TestPlaylistMutationScopeIsExplicit(t *testing.T) {
	playlistScope := domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopePlaylists, SelectedIDs: []string{"playlist-a"}}
	require.True(t, playlistInScope(playlistScope, "playlist-a"))
	require.False(t, playlistInScope(playlistScope, "playlist-b"))
	require.True(t, playlistInScope(domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAll}, "playlist-b"))
	require.False(t, playlistInScope(domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeArtists, SelectedIDs: []string{"artist-a"}}, "playlist-a"))
}
