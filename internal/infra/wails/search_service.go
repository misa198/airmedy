package wails

import (
	"context"
	"strings"

	"airmedy/internal/domain"
)

type SearchResultSet struct {
	Tracks         []*domain.TrackDTO             `json:"tracks"`
	Albums         []*domain.AlbumDTO             `json:"albums"`
	Artists        []*domain.Artist               `json:"artists"`
	Playlists      []*domain.Playlist             `json:"playlists"`
	PlaylistTracks map[string][]*domain.TrackDTO `json:"playlist_tracks"`
	Composers      []*domain.Composer             `json:"composers"`
}

type SearchService struct {
	search    domain.SearchService
	tracks    domain.TrackRepository
	albums    domain.AlbumRepository
	artists   domain.ArtistRepository
	playlists domain.PlaylistRepository
	composers domain.ComposerRepository
}

func NewSearchService(
	search domain.SearchService,
	tracks domain.TrackRepository,
	albums domain.AlbumRepository,
	artists domain.ArtistRepository,
	playlists domain.PlaylistRepository,
	composers domain.ComposerRepository,
) *SearchService {
	return &SearchService{
		search:    search,
		tracks:    tracks,
		albums:    albums,
		artists:   artists,
		playlists: playlists,
		composers: composers,
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

	result := &SearchResultSet{
		PlaylistTracks: make(map[string][]*domain.TrackDTO),
	}

	seen := make(map[string]bool)
	for _, r := range raw {
		// Normalize type string (Bleve might return it differently depending on index state)
		typ := strings.ToLower(r.Type)
		
		// Skip duplicates
		key := typ + ":" + r.ID
		if seen[key] {
			continue
		}
		seen[key] = true
		
		switch typ {
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
		case "playlist":
			p, err := s.playlists.GetByID(ctx, r.ID)
			if err != nil || p == nil {
				continue
			}
			result.Playlists = append(result.Playlists, p)

			// Get tracks for playlist artwork grid
			pt, err := s.playlists.GetTracks(ctx, p.ID)
			if err == nil {
				if len(pt) > 4 {
					pt = pt[:4]
				}
				result.PlaylistTracks[p.ID] = pt
			}
		case "composer":
			c, err := s.composers.GetByID(ctx, r.ID)
			if err != nil || c == nil {
				continue
			}
			result.Composers = append(result.Composers, c)
		}
	}

	return result, nil
}
