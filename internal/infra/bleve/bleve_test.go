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
	track1 := &domain.TrackDTO{
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
	track2 := &domain.TrackDTO{
		Track: domain.Track{
			ID:    "track-2",
			Title: "More Than You Know",
		},
		Artists: []*domain.Artist{
			{Name: "Axwell /\\ Ingrosso"},
		},
		Album: &domain.Album{
			Title: "More Than You Know",
		},
	}

	err = service.IndexTrack(ctx, track1)
	if err != nil {
		t.Fatalf("Failed to index track 1: %v", err)
	}
	err = service.IndexTrack(ctx, track2)
	if err != nil {
		t.Fatalf("Failed to index track 2: %v", err)
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

	results, err = service.Search(ctx, "more than")
	if err != nil {
		t.Fatalf("Failed to search: %v", err)
	}
	if len(results) == 0 {
		t.Errorf("Expected result when searching for 'more than', got 0")
	}
}
