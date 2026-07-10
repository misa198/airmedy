package moodradio

import (
	"context"
	"testing"

	"airmedy/internal/domain"
)

type trackQueryRepositoryStub struct {
	candidates []*domain.TrackDTO
	seed       string
	excludes   []string
	limit      int
}

func (s *trackQueryRepositoryStub) FindSimilar(_ context.Context, seedTrackID string, excludeTrackIDs []string, limit int) ([]*domain.TrackDTO, error) {
	s.seed = seedTrackID
	s.excludes = append([]string(nil), excludeTrackIDs...)
	s.limit = limit
	return append([]*domain.TrackDTO(nil), s.candidates...), nil
}

func (s *trackQueryRepositoryStub) MoodDensityGrid(context.Context, int) (*domain.MoodDensityGrid, error) {
	return nil, nil
}

func TestServiceGenerateUsesCandidatePoolAndForwardsExclusions(t *testing.T) {
	candidates := make([]*domain.TrackDTO, 90)
	for i := range candidates {
		candidates[i] = track("track-"+string(rune('a'+i%26)), "album-"+string(rune('a'+i%26)), "artist-"+string(rune('a'+i%26)))
	}
	repo := &trackQueryRepositoryStub{candidates: candidates}
	service := NewService(repo)
	service.randomFloat64 = func() float64 { return 0 }

	got, err := service.Generate(context.Background(), "seed", []string{"queued-1", "queued-2"}, 15)
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if repo.seed != "seed" || repo.limit != candidateLimit || len(repo.excludes) != 2 {
		t.Fatalf("unexpected repository request: seed=%q limit=%d excludes=%v", repo.seed, repo.limit, repo.excludes)
	}
	if len(got) != 15 {
		t.Fatalf("expected 15 selected tracks, got %d", len(got))
	}
	seen := map[string]struct{}{}
	for _, candidate := range got {
		if _, exists := seen[candidate.ID]; exists {
			t.Fatalf("duplicate selected track %q", candidate.ID)
		}
		seen[candidate.ID] = struct{}{}
	}
}

func TestServiceGenerateDiversifiesArtistAndAlbumWhenAlternativesExist(t *testing.T) {
	repo := &trackQueryRepositoryStub{candidates: []*domain.TrackDTO{
		track("one", "album-a", "artist-a"),
		track("two", "album-b", "artist-a"),
		track("three", "album-a", "artist-b"),
		track("four", "album-c", "artist-c"),
	}}
	service := NewService(repo)
	service.randomFloat64 = func() float64 { return 0 }

	got, err := service.Generate(context.Background(), "seed", nil, 3)
	if err != nil {
		t.Fatalf("Generate: %v", err)
	}
	if got[0].ID != "one" || got[1].ID != "four" || got[2].ID != "three" {
		t.Fatalf("expected diversity-aware order [one four three], got %v", trackIDs(got))
	}
}

func track(id, albumID, artistID string) *domain.TrackDTO {
	return &domain.TrackDTO{
		Track:   domain.Track{ID: id, AlbumID: albumID},
		Artists: []*domain.Artist{{ID: artistID}},
	}
}

func trackIDs(tracks []*domain.TrackDTO) []string {
	ids := make([]string, len(tracks))
	for i, track := range tracks {
		ids[i] = track.ID
	}
	return ids
}
