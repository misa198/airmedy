package wails

import (
	"context"

	"airmedy/internal/domain"
)

type SearchResultSet struct {
	Tracks  []*domain.TrackDTO  `json:"tracks"`
	Albums  []*domain.AlbumDTO  `json:"albums"`
	Artists []*domain.Artist    `json:"artists"`
}

type SearchService struct {
	search   domain.SearchService
	tracks   domain.TrackRepository
	albums   domain.AlbumRepository
	artists  domain.ArtistRepository
}

func NewSearchService(
	search domain.SearchService,
	tracks domain.TrackRepository,
	albums domain.AlbumRepository,
	artists domain.ArtistRepository,
) *SearchService {
	return &SearchService{
		search:  search,
		tracks:  tracks,
		albums:  albums,
		artists: artists,
	}
}

func (s *SearchService) Search(query string) (*SearchResultSet, error) {
	if query == "" {
		return &SearchResultSet{}, nil
	}

	ctx := context.Background()
	raw, err := s.search.Search(ctx, query)
	if err != nil {
		return nil, err
	}

	result := &SearchResultSet{}
	for _, r := range raw {
		switch r.Type {
		case "track":
			t, err := s.tracks.GetByID(ctx, r.ID)
			if err != nil || t == nil {
				continue
			}
			result.Tracks = append(result.Tracks, t)
		case "album":
			a, err := s.albums.GetByID(ctx, r.ID)
			if err != nil || a == nil {
				continue
			}
			result.Albums = append(result.Albums, a)
		case "artist":
			ar, err := s.artists.GetByID(ctx, r.ID)
			if err != nil || ar == nil {
				continue
			}
			result.Artists = append(result.Artists, ar)
		}
	}

	return result, nil
}
