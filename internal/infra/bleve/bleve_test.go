package bleve

import (
	"context"
	"os"
	"testing"

	"airmedy/internal/domain"
)

func TestBleveSearchService(t *testing.T) {
	indexPath := "test.bleve"
	defer func() { _ = os.RemoveAll(indexPath) }()

	service, err := NewBleveSearchService(indexPath)
	if err != nil {
		t.Fatalf("Failed to create search service: %v", err)
	}
	defer func() { _ = service.Close() }()

	ctx := context.Background()
	track := &domain.TrackDTO{
		Track: domain.Track{
			ID:    "track-1",
			Title: "Bohemian Rhapsody",
		},
		Artists: []*domain.Artist{
			{Name: "Queen"},
		},
		Album: &domain.Album{
			Title: "A Night at the Opera",
		},
	}

	err = service.IndexTrack(ctx, track)
	if err != nil {
		t.Fatalf("Failed to index track: %v", err)
	}

	results, err := service.Search(ctx, "Bohemian")
	if err != nil {
		t.Fatalf("Failed to search: %v", err)
	}

	if len(results) == 0 {
		t.Errorf("Expected results, got 0")
	} else if results[0].ID != "track-1" {
		t.Errorf("Expected ID 'track-1', got '%s'", results[0].ID)
	}

	results, err = service.Search(ctx, "Queen")
	if err != nil {
		t.Fatalf("Failed to search: %v", err)
	}
	if len(results) == 0 {
		t.Errorf("Expected results for artist search, got 0")
	}
}
