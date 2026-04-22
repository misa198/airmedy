package bleve

import (
	"context"
	"fmt"
	"os"

	"changeme/internal/domain"

	"github.com/blevesearch/bleve/v2"
	"github.com/blevesearch/bleve/v2/mapping"
)

type bleveSearchService struct {
	index bleve.Index
}

func NewBleveSearchService(indexPath string) (domain.SearchService, error) {
	var index bleve.Index
	var err error

	if _, err = os.Stat(indexPath); os.IsNotExist(err) {
		indexMapping := buildIndexMapping()
		index, err = bleve.New(indexPath, indexMapping)
		if err != nil {
			return nil, fmt.Errorf("failed to create bleve index: %w", err)
		}
	} else {
		index, err = bleve.Open(indexPath)
		if err != nil {
			return nil, fmt.Errorf("failed to open bleve index: %w", err)
		}
	}

	return &bleveSearchService{index: index}, nil
}

func buildIndexMapping() mapping.IndexMapping {
	trackMapping := bleve.NewDocumentMapping()

	// Text fields for tracks
	titleFieldMapping := bleve.NewTextFieldMapping()
	titleFieldMapping.Analyzer = "en"
	trackMapping.AddFieldMappingsAt("title", titleFieldMapping)

	artistFieldMapping := bleve.NewTextFieldMapping()
	artistFieldMapping.Analyzer = "en"
	trackMapping.AddFieldMappingsAt("artist_name", artistFieldMapping)
	trackMapping.AddFieldMappingsAt("artist_names", artistFieldMapping)

	albumFieldMapping := bleve.NewTextFieldMapping()
	albumFieldMapping.Analyzer = "en"
	trackMapping.AddFieldMappingsAt("album_name", albumFieldMapping)

	trackMapping.AddFieldMappingsAt("genres", artistFieldMapping)

	// Album mapping
	albumDocMapping := bleve.NewDocumentMapping()
	albumDocMapping.AddFieldMappingsAt("title", titleFieldMapping)
	albumDocMapping.AddFieldMappingsAt("artist_name", artistFieldMapping)
	albumDocMapping.AddFieldMappingsAt("artist_names", artistFieldMapping)

	// Artist mapping
	artistDocMapping := bleve.NewDocumentMapping()
	artistDocMapping.AddFieldMappingsAt("name", artistFieldMapping)

	indexMapping := bleve.NewIndexMapping()
	indexMapping.AddDocumentMapping("track", trackMapping)
	indexMapping.AddDocumentMapping("album", albumDocMapping)
	indexMapping.AddDocumentMapping("artist", artistDocMapping)

	return indexMapping
}

func (s *bleveSearchService) IndexTrack(ctx context.Context, track *domain.TrackDTO) error {
	doc := map[string]interface{}{
		"id":    track.Track.ID,
		"type":  "track",
		"title": track.Track.Title,
	}

	var artistNames []string
	for _, a := range track.Artists {
		artistNames = append(artistNames, a.Name)
	}
	if len(artistNames) > 0 {
		doc["artist_names"] = artistNames
		doc["artist_name"] = artistNames[0] // fallback for old queries
	}

	if track.Album != nil {
		doc["album_name"] = track.Album.Title
	}

	var genreNames []string
	for _, g := range track.Genres {
		genreNames = append(genreNames, g.Name)
	}
	if len(genreNames) > 0 {
		doc["genres"] = genreNames
	}

	return s.index.Index(track.Track.ID, doc)
}

func (s *bleveSearchService) IndexAlbum(ctx context.Context, album *domain.AlbumDTO) error {
	doc := map[string]interface{}{
		"id":    album.Album.ID,
		"type":  "album",
		"title": album.Album.Title,
	}

	var artistNames []string
	for _, a := range album.Artists {
		artistNames = append(artistNames, a.Name)
	}
	if len(artistNames) > 0 {
		doc["artist_names"] = artistNames
		doc["artist_name"] = artistNames[0]
	}

	return s.index.Index(album.Album.ID, doc)
}

func (s *bleveSearchService) IndexArtist(ctx context.Context, artist *domain.Artist) error {
	doc := map[string]interface{}{
		"id":   artist.ID,
		"type": "artist",
		"name": artist.Name,
	}
	return s.index.Index(artist.ID, doc)
}

func (s *bleveSearchService) Search(ctx context.Context, queryStr string) ([]domain.SearchResult, error) {
	query := bleve.NewMatchQuery(queryStr)
	searchRequest := bleve.NewSearchRequest(query)
	searchRequest.Fields = []string{"id", "type"}
	searchRequest.Size = 50

	searchResults, err := s.index.Search(searchRequest)
	if err != nil {
		return nil, fmt.Errorf("failed to search: %w", err)
	}

	var results []domain.SearchResult
	for _, hit := range searchResults.Hits {
		results = append(results, domain.SearchResult{
			ID:    hit.ID,
			Type:  fmt.Sprintf("%v", hit.Fields["type"]),
			Score: hit.Score,
		})
	}

	return results, nil
}

func (s *bleveSearchService) DeleteFromIndex(ctx context.Context, id string) error {
	return s.index.Delete(id)
}

func (s *bleveSearchService) Close() error {
	return s.index.Close()
}
