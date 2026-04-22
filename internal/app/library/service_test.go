package library

import (
	"context"
	"log/slog"
	"os"
	"path/filepath"
	"testing"

	"changeme/internal/domain"
)

type mockTrackRepo struct {
	domain.TrackRepository
	tracks map[string]*domain.Track
}

func (m *mockTrackRepo) Upsert(ctx context.Context, track *domain.Track) error {
	m.tracks[track.Path] = track
	return nil
}

type mockAlbumRepo struct{ domain.AlbumRepository }
func (m *mockAlbumRepo) Upsert(ctx context.Context, album *domain.Album) error { return nil }

type mockArtistRepo struct{ domain.ArtistRepository }
func (m *mockArtistRepo) Upsert(ctx context.Context, artist *domain.Artist) error { return nil }

type mockGenreRepo struct{ domain.GenreRepository }
func (m *mockGenreRepo) Upsert(ctx context.Context, genre *domain.Genre) error { return nil }

type mockComposerRepo struct{ domain.ComposerRepository }
func (m *mockComposerRepo) Upsert(ctx context.Context, composer *domain.Composer) error { return nil }

type mockFolderRepo struct{ domain.WatchedFolderRepository }
func (m *mockFolderRepo) Save(ctx context.Context, folder *domain.WatchedFolder) error { return nil }

type mockMetadataExtractor struct{ domain.MetadataExtractor }
func (m *mockMetadataExtractor) Extract(ctx context.Context, path string) (*domain.Track, error) {
	return &domain.Track{
		Path:       path,
		Title:      filepath.Base(path),
		ArtistName: "Mock Artist",
		AlbumName:  "Mock Album",
	}, nil
}

type mockSearchService struct{ domain.SearchService }
func (m *mockSearchService) IndexTrack(ctx context.Context, track *domain.Track) error { return nil }
func (m *mockSearchService) Close() error { return nil }

func TestLibraryService_SyncFolder(t *testing.T) {
	// Create a temporary directory for testing sync
	tempDir, err := os.MkdirTemp("", "airmedy_test_sync")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer os.RemoveAll(tempDir)

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
		&mockFolderRepo{},
		&mockMetadataExtractor{},
		&mockSearchService{},
		slog.Default(),
	)
	if err != nil {
		t.Fatalf("Failed to create library service: %v", err)
	}
	defer s.Stop(context.Background())

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
}
