package moodradio

import (
	"context"
	"math"
	"math/rand/v2"

	"airmedy/internal/domain"
)

const (
	candidateLimit        = 80
	openingCandidateLimit = 20
	openingTrackCount     = 3
	artistCooldown        = 3
)

// Service turns deterministic nearest-neighbour candidates into a varied
// Mood Radio batch while preserving the seed's audio characteristics.
type Service struct {
	tracks        domain.TrackQueryRepository
	randomFloat64 func() float64
}

func NewService(tracks domain.TrackQueryRepository) *Service {
	return &Service{tracks: tracks, randomFloat64: rand.Float64}
}

// Generate returns a varied batch of tracks related to seedTrackID. Tracks in
// excludeTrackIDs are removed before candidate ranking, so queue refills do
// not run out merely because their closest neighbours were already queued.
func (s *Service) Generate(ctx context.Context, seedTrackID string, excludeTrackIDs []string, limit int) ([]*domain.TrackDTO, error) {
	if limit <= 0 {
		return nil, nil
	}
	candidates, err := s.tracks.FindSimilar(ctx, seedTrackID, excludeTrackIDs, max(candidateLimit, limit))
	if err != nil {
		return nil, err
	}
	return s.selectTracks(candidates, limit), nil
}

func (s *Service) selectTracks(candidates []*domain.TrackDTO, limit int) []*domain.TrackDTO {
	if limit > len(candidates) {
		limit = len(candidates)
	}
	selected := make([]*domain.TrackDTO, 0, limit)
	usedAlbums := make(map[string]struct{})
	recentArtists := make([]string, 0, artistCooldown)

	for len(selected) < limit && len(candidates) > 0 {
		windowSize := len(candidates)
		if len(selected) < openingTrackCount {
			windowSize = min(windowSize, openingCandidateLimit)
		}
		window := candidates[:windowSize]
		index := s.pickCandidate(window, usedAlbums, recentArtists)
		track := candidates[index]
		selected = append(selected, track)
		candidates = append(candidates[:index], candidates[index+1:]...)

		if track.AlbumID != "" {
			usedAlbums[track.AlbumID] = struct{}{}
		}
		if artistID := primaryArtistID(track); artistID != "" {
			recentArtists = append(recentArtists, artistID)
			if len(recentArtists) > artistCooldown {
				recentArtists = recentArtists[1:]
			}
		}
	}
	return selected
}

// pickCandidate enforces album and artist diversity when possible. It first
// relaxes the album rule, then the artist cooldown, so small libraries still
// get a full radio batch.
func (s *Service) pickCandidate(window []*domain.TrackDTO, usedAlbums map[string]struct{}, recentArtists []string) int {
	for _, rules := range []struct{ album, artist bool }{{true, true}, {false, true}, {false, false}} {
		eligible := make([]int, 0, len(window))
		for i, track := range window {
			if rules.album && track.AlbumID != "" {
				if _, used := usedAlbums[track.AlbumID]; used {
					continue
				}
			}
			if rules.artist && contains(recentArtists, primaryArtistID(track)) {
				continue
			}
			eligible = append(eligible, i)
		}
		if len(eligible) > 0 {
			return eligible[s.weightedIndex(eligible)]
		}
	}
	return 0
}

func (s *Service) weightedIndex(eligible []int) int {
	total := 0.0
	for _, index := range eligible {
		total += rankWeight(index)
	}
	target := s.randomFloat64() * total
	for i, index := range eligible {
		target -= rankWeight(index)
		if target <= 0 {
			return i
		}
	}
	return len(eligible) - 1
}

func rankWeight(rank int) float64 { return 1 / math.Sqrt(float64(rank+1)) }

func primaryArtistID(track *domain.TrackDTO) string {
	if len(track.Artists) == 0 || track.Artists[0] == nil {
		return ""
	}
	return track.Artists[0].ID
}

func contains(values []string, value string) bool {
	if value == "" {
		return false
	}
	for _, current := range values {
		if current == value {
			return true
		}
	}
	return false
}
