package library

import (
	"context"
	"log/slog"
	"os"
	"path/filepath"
	"testing"

	"airmedy/internal/domain"
)

func (m *mockTrackRepo) GetByID(ctx context.Context, id string) (*domain.TrackDTO, error) {
	for _, track := range m.tracks {
		if track.ID == id {
			return &domain.TrackDTO{Track: *track}, nil
		}
	}
	return nil, nil
}

func (m *mockTrackRepo) ToggleFavorite(ctx context.Context, id string) (bool, error) {
	for _, track := range m.tracks {
		if track.ID == id {
			track.IsFavorite = !track.IsFavorite
			return track.IsFavorite, nil
		}
	}
	return false, nil
}

func TestLibraryService_AnalysisListener_FiresOnImportNotOnFavoriteToggle(t *testing.T) {
	tempDir, err := os.MkdirTemp("", "airmedy_test_analysis_listener")
	if err != nil {
		t.Fatalf("Failed to create temp dir: %v", err)
	}
	defer func() { _ = os.RemoveAll(tempDir) }()

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
		&mockSyncStateRepo{},
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

	var fired []string
	s.AddAnalysisListener(func(trackID string) {
		fired = append(fired, trackID)
	})

	if err := s.SyncFolder(context.Background(), tempDir); err != nil {
		t.Fatalf("SyncFolder failed: %v", err)
	}
	if len(fired) != 1 {
		t.Fatalf("expected 1 analysis-pending notification after import, got %d: %v", len(fired), fired)
	}

	track := trackRepo.tracks[dummyFile]
	if track == nil {
		t.Fatalf("expected imported track in repo")
	}
	if fired[0] != track.ID {
		t.Errorf("expected notification for track ID %q, got %q", track.ID, fired[0])
	}

	// Favorite toggling must NOT trigger re-analysis (DSP features don't change).
	if _, err := s.ToggleFavorite(context.Background(), track.ID); err != nil {
		t.Fatalf("ToggleFavorite: %v", err)
	}
	if len(fired) != 1 {
		t.Fatalf("expected no additional analysis-pending notification after favorite toggle, got %d: %v", len(fired), fired)
	}
}
