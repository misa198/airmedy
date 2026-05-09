package library

import (
	"context"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"airmedy/internal/domain"
)

type mockTrackRepo struct {
	domain.TrackRepository
	tracks map[string]*domain.Track
	deletedPrefix string
}

func (m *mockTrackRepo) Upsert(ctx context.Context, track *domain.Track) error {
	m.tracks[track.Path] = track
	return nil
}

func (m *mockTrackRepo) GetByPath(ctx context.Context, path string) (*domain.TrackDTO, error) {
	if track, ok := m.tracks[path]; ok {
		return &domain.TrackDTO{Track: *track}, nil
	}
	return nil, nil
}

func (m *mockTrackRepo) GetByPathPrefix(ctx context.Context, prefix string) ([]*domain.TrackDTO, error) {
	var results []*domain.TrackDTO
	for path, track := range m.tracks {
		if strings.HasPrefix(path, prefix) {
			results = append(results, &domain.TrackDTO{Track: *track})
		}
	}
	return results, nil
}

func (m *mockTrackRepo) DeleteByPathPrefix(ctx context.Context, prefix string) error {
	m.deletedPrefix = prefix
	for path := range m.tracks {
		if strings.HasPrefix(path, prefix) {
			delete(m.tracks, path)
		}
	}
	return nil
}

func (m *mockTrackRepo) Delete(ctx context.Context, id string) error {
	for path, track := range m.tracks {
		if track.ID == id {
			delete(m.tracks, path)
			return nil
		}
	}
	return nil
}

func (m *mockTrackRepo) GetAllArtworkKeys(ctx context.Context) ([]string, error) {
	return nil, nil
}
func (m *mockTrackRepo) SetArtists(ctx context.Context, trackID string, artistIDs []string) error {
	return nil
}
func (m *mockTrackRepo) SetAlbumArtists(ctx context.Context, trackID string, artistIDs []string) error {
	return nil
}
func (m *mockTrackRepo) SetGenres(ctx context.Context, trackID string, genreIDs []string) error {
	return nil
}
func (m *mockTrackRepo) SetComposers(ctx context.Context, trackID string, composerIDs []string) error {
	return nil
}

type mockAlbumRepo struct{ domain.AlbumRepository }
func (m *mockAlbumRepo) Upsert(ctx context.Context, album *domain.Album) error { return nil }
func (m *mockAlbumRepo) DeleteOrphaned(ctx context.Context) error { return nil }
func (m *mockAlbumRepo) GetByID(ctx context.Context, id string) (*domain.AlbumDTO, error) { return nil, nil }
func (m *mockAlbumRepo) GetByNormalizationKey(ctx context.Context, key string) (*domain.Album, error) {
	return nil, nil
}
func (m *mockAlbumRepo) SetArtists(ctx context.Context, albumID string, artistIDs []string) error {
	return nil
}

type mockArtistRepo struct{ domain.ArtistRepository }

func (m *mockArtistRepo) Upsert(ctx context.Context, artist *domain.Artist) error { return nil }
func (m *mockArtistRepo) DeleteOrphaned(ctx context.Context) error { return nil }
func (m *mockArtistRepo) GetByNormalizationKey(ctx context.Context, key string) (*domain.Artist, error) {
	return nil, nil
}
func (m *mockArtistRepo) GetAll(ctx context.Context) ([]*domain.Artist, error) { return nil, nil }

type mockGenreRepo struct{ domain.GenreRepository }

func (m *mockGenreRepo) Upsert(ctx context.Context, genre *domain.Genre) error { return nil }
func (m *mockGenreRepo) DeleteOrphaned(ctx context.Context) error { return nil }
func (m *mockGenreRepo) GetByNormalizationKey(ctx context.Context, key string) (*domain.Genre, error) {
	return nil, nil
}

type mockComposerRepo struct{ domain.ComposerRepository }

func (m *mockComposerRepo) Upsert(ctx context.Context, composer *domain.Composer) error { return nil }
func (m *mockComposerRepo) DeleteOrphaned(ctx context.Context) error { return nil }
func (m *mockComposerRepo) GetByNormalizationKey(ctx context.Context, key string) (*domain.Composer, error) {
	return nil, nil
}

type mockPlaylistRepo struct{ domain.PlaylistRepository }

type mockFolderRepo struct {
	domain.WatchedFolderRepository
	folders []*domain.WatchedFolder
	deletedID string
}

func (m *mockFolderRepo) Save(ctx context.Context, folder *domain.WatchedFolder) error {
	m.folders = append(m.folders, folder)
	return nil
}

func (m *mockFolderRepo) GetAll(ctx context.Context) ([]*domain.WatchedFolder, error) {
	return m.folders, nil
}

func (m *mockFolderRepo) GetByID(ctx context.Context, id string) (*domain.WatchedFolder, error) {
	for _, f := range m.folders {
		if f.ID == id {
			return f, nil
		}
	}
	return nil, nil
}

func (m *mockFolderRepo) Delete(ctx context.Context, id string) error {
	m.deletedID = id
	return nil
}

type mockSettingsRepo struct {
	domain.SettingsRepository
}

func (m *mockSettingsRepo) Load(ctx context.Context) (*domain.AppSettings, error) {
	return &domain.AppSettings{UseOnlineArtistArtwork: false}, nil
}

type mockMetadataExtractor struct {
	domain.MetadataExtractor
	callCount int
}

func (m *mockMetadataExtractor) Extract(ctx context.Context, path string) (*domain.TrackDTO, error) {
	m.callCount++
	return &domain.TrackDTO{
		Track: domain.Track{
			Path:  path,
			Title: filepath.Base(path),
		},
		Artists: []*domain.Artist{{Name: "Mock Artist"}},
		Album:   &domain.Album{Title: "Mock Album"},
	}, nil
}
func (m *mockMetadataExtractor) ExtractArtwork(ctx context.Context, path string) ([]byte, string, error) {
	return nil, "", nil
}
func (m *mockMetadataExtractor) ExtractLyrics(ctx context.Context, path string) (string, bool, error) {
	return "", false, nil
}

type mockSearchService struct{ domain.SearchService }
func (m *mockSearchService) IndexTrack(ctx context.Context, track *domain.TrackDTO) error { return nil }
func (m *mockSearchService) IndexAlbum(ctx context.Context, album *domain.AlbumDTO) error { return nil }
func (m *mockSearchService) IndexArtist(ctx context.Context, artist *domain.Artist) error { return nil }
func (m *mockSearchService) IndexComposer(ctx context.Context, composer *domain.Composer) error { return nil }
func (m *mockSearchService) IndexPlaylist(ctx context.Context, playlist *domain.Playlist) error { return nil }
func (m *mockSearchService) Close() error { return nil }
func (m *mockSearchService) DeleteFromIndex(ctx context.Context, id string) error { return nil }

type mockArtworkCache struct{ domain.ArtworkCache }
func (m *mockArtworkCache) Save(ctx context.Context, data []byte, mimeType string) (string, error) { return "", nil }
func (m *mockArtworkCache) CleanupOrphaned(ctx context.Context, activeKeys map[string]bool) error { return nil }

type mockMetadataWriter struct{}
func (m *mockMetadataWriter) WriteMetadata(_ context.Context, _ string, _ domain.MetadataUpdate) error { return nil }

func TestLibraryService_SyncFolder(t *testing.T) {
	// Create a temporary directory for testing sync
	tempDir, err := os.MkdirTemp("", "airmedy_test_sync")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer func() { _ = os.RemoveAll(tempDir) }()

	// Create a dummy music file
	dummyFile := filepath.Join(tempDir, "test.mp3")
	if err := os.WriteFile(dummyFile, []byte("dummy"), 0644); err != nil {
		t.Fatalf("Failed to create dummy file: %v", err)
	}

	trackRepo := &mockTrackRepo{tracks: make(map[string]*domain.Track)}
	
	s, err := NewLibraryService(
		trackRepo,
		&mockAlbumRepo{},
		&mockArtistRepo{},
		&mockGenreRepo{},
		&mockComposerRepo{},
		&mockPlaylistRepo{},
		&mockFolderRepo{},
		&mockSettingsRepo{},
		&mockMetadataExtractor{},
		&mockMetadataWriter{},
		&mockArtworkCache{},
		&mockSearchService{},
		nil,
		slog.Default(),
	)
	if err != nil {
		t.Fatalf("Failed to create library service: %v", err)
	}
	defer func() { _ = s.Stop(context.Background()) }()

	err = s.SyncFolder(context.Background(), tempDir)
	if err != nil {
		t.Fatalf("SyncFolder failed: %v", err)
	}

	// Verify the track was "imported" into our mock repo
	if len(trackRepo.tracks) != 1 {
		t.Errorf("Expected 1 track in repo, got %d", len(trackRepo.tracks))
	}

	if track, ok := trackRepo.tracks[dummyFile]; !ok {
		t.Errorf("Track with path %s not found in repo", dummyFile)
	} else if track.Title != "test.mp3" {
		t.Errorf("Expected title 'test.mp3', got '%s'", track.Title)
	}

	// Verify optimization: Sync again, Extract should NOT be called
	extractor := s.metadataExtractor.(*mockMetadataExtractor)
	initialCalls := extractor.callCount
	if initialCalls != 1 {
		t.Errorf("Expected 1 call to Extract, got %d", initialCalls)
	}

	err = s.SyncFolder(context.Background(), tempDir)
	if err != nil {
		t.Fatalf("Second SyncFolder failed: %v", err)
	}

	if extractor.callCount != initialCalls {
		t.Errorf("Optimization failed: Extract was called during second sync. Initial: %d, Current: %d", initialCalls, extractor.callCount)
	}

	// Verify that modification triggers a re-import
	// Small delay to ensure mtime change
	time.Sleep(1 * time.Second)
	if err := os.WriteFile(dummyFile, []byte("changed"), 0644); err != nil {
		t.Fatalf("Failed to update dummy file: %v", err)
	}

	err = s.SyncFolder(context.Background(), tempDir)
	if err != nil {
		t.Fatalf("Third SyncFolder failed: %v", err)
	}

	if extractor.callCount != initialCalls+1 {
		t.Errorf("Extract should have been called after file modification. Expected %d, got %d", initialCalls+1, extractor.callCount)
	}
}

func TestLibraryService_SyncFolder_SupportedExtensions(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "airmedy_test_exts")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer func() { _ = os.RemoveAll(tempDir) }()

	extensions := []string{
		"mp3", "flac", "m4a", "wav", "ogg", "opus",
		"aiff", "aif", "ape", "wv", "dsf", "dff",
	}
	for _, ext := range extensions {
		path := filepath.Join(tempDir, "track."+ext)
		if err := os.WriteFile(path, []byte("dummy"), 0644); err != nil {
			t.Fatalf("Failed to create %s file: %v", ext, err)
		}
	}

	// Create a file with an unsupported extension — must be ignored.
	unsupported := filepath.Join(tempDir, "notes.txt")
	if err := os.WriteFile(unsupported, []byte("ignored"), 0644); err != nil {
		t.Fatalf("Failed to create unsupported file: %v", err)
	}

	trackRepo := &mockTrackRepo{tracks: make(map[string]*domain.Track)}
	s, err := NewLibraryService(
		trackRepo,
		&mockAlbumRepo{},
		&mockArtistRepo{},
		&mockGenreRepo{},
		&mockComposerRepo{},
		&mockPlaylistRepo{},
		&mockFolderRepo{},
		&mockSettingsRepo{},
		&mockMetadataExtractor{},
		&mockMetadataWriter{},
		&mockArtworkCache{},
		&mockSearchService{},
		nil,
		slog.Default(),
	)
	if err != nil {
		t.Fatalf("Failed to create library service: %v", err)
	}
	defer func() { _ = s.Stop(context.Background()) }()

	if err := s.SyncFolder(context.Background(), tempDir); err != nil {
		t.Fatalf("SyncFolder failed: %v", err)
	}

	if len(trackRepo.tracks) != len(extensions) {
		t.Errorf("Expected %d tracks (one per extension), got %d", len(extensions), len(trackRepo.tracks))
	}
	if _, found := trackRepo.tracks[unsupported]; found {
		t.Error("Unsupported .txt file was imported — should have been ignored")
	}
}

func TestLibraryService_AddWatchedFolder_CoveringExisting(t *testing.T) {
	trackRepo := &mockTrackRepo{tracks: make(map[string]*domain.Track)}
	folderRepo := &mockFolderRepo{
		folders: []*domain.WatchedFolder{
			{ID: "child-id", Path: "/Music/A/B"},
		},
	}

	s, _ := NewLibraryService(
		trackRepo,
		&mockAlbumRepo{},
		&mockArtistRepo{},
		&mockGenreRepo{},
		&mockComposerRepo{},
		&mockPlaylistRepo{},
		folderRepo,
		&mockSettingsRepo{},
		&mockMetadataExtractor{},
		&mockMetadataWriter{},
		&mockArtworkCache{},
		&mockSearchService{},
		nil,
		slog.Default(),
	)
	defer func() { _ = s.Stop(context.Background()) }()

	// Add parent folder /Music/A
	err := s.AddWatchedFolder(context.Background(), "/Music/A")
	if err != nil {
		t.Fatalf("AddWatchedFolder failed: %v", err)
	}

	// Verify child folder was deleted from repo
	if folderRepo.deletedID != "child-id" {
		t.Errorf("Expected child folder to be deleted, got deletedID: %s", folderRepo.deletedID)
	}

	// Verify tracks were NOT deleted (deletedPrefix should be empty)
	if trackRepo.deletedPrefix != "" {
		t.Errorf("Expected tracks NOT to be deleted, but DeleteByPathPrefix was called with prefix: %s", trackRepo.deletedPrefix)
	}

	// Verify new parent folder was added
	found := false
	for _, f := range folderRepo.folders {
		if f.Path == "/Music/A" {
			found = true
			break
		}
	}
	if !found {
		t.Error("Parent folder /Music/A not found in repo after AddWatchedFolder")
	}
}
