package bleve

import (
	"context"
	"fmt"
	"os"
	"strings"

	"airmedy/internal/domain"

	"github.com/blevesearch/bleve/v2"
	"github.com/blevesearch/bleve/v2/mapping"
	"github.com/blevesearch/bleve/v2/search/query"
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
	indexMapping := bleve.NewIndexMapping()

	// 1. Text field mapping for searchable content
	textFieldMapping := bleve.NewTextFieldMapping()
	textFieldMapping.Analyzer = "standard"
	textFieldMapping.Store = false // id/type (keyword) are enough for result retrieval

	// 2. Keyword field mapping for IDs and Types (exact match only)
	keywordFieldMapping := bleve.NewTextFieldMapping()
	keywordFieldMapping.Analyzer = "keyword"
	keywordFieldMapping.Store = true
	keywordFieldMapping.Index = true

	// 3. Create a single, global document mapping
	defaultMapping := bleve.NewDocumentMapping()
	
	// Core identity fields
	defaultMapping.AddFieldMappingsAt("type", keywordFieldMapping)
	defaultMapping.AddFieldMappingsAt("id", keywordFieldMapping)
	
	// Searchable fields (Standardized on 'title' for names)
	defaultMapping.AddFieldMappingsAt("title", textFieldMapping)
	defaultMapping.AddFieldMappingsAt("name", textFieldMapping) // redundant fallback
	
	// Category-specific fields
	defaultMapping.AddFieldMappingsAt("artist_name", textFieldMapping)
	defaultMapping.AddFieldMappingsAt("artist_names", textFieldMapping)
	defaultMapping.AddFieldMappingsAt("album_name", textFieldMapping)
	defaultMapping.AddFieldMappingsAt("genres", textFieldMapping)
	defaultMapping.AddFieldMappingsAt("description", textFieldMapping)

	indexMapping.DefaultMapping = defaultMapping

	return indexMapping
}

func (s *bleveSearchService) IndexTrack(ctx context.Context, track *domain.TrackDTO) error {
	doc := map[string]interface{}{
		"id":    track.ID,
		"type":  "track",
		"title": domain.FoldUnicode(track.Title),
	}

	var artistNames []string
	for _, a := range track.Artists {
		artistNames = append(artistNames, domain.FoldUnicode(a.Name))
	}
	if len(artistNames) > 0 {
		doc["artist_names"] = artistNames
		doc["artist_name"] = artistNames[0]
	}

	if track.Album != nil {
		doc["album_name"] = domain.FoldUnicode(track.Album.Title)
	}

	var genreNames []string
	for _, g := range track.Genres {
		genreNames = append(genreNames, domain.FoldUnicode(g.Name))
	}
	if len(genreNames) > 0 {
		doc["genres"] = genreNames
	}

	return s.index.Index("track:"+track.ID, doc)
}

func (s *bleveSearchService) IndexAlbum(ctx context.Context, album *domain.AlbumDTO) error {
	doc := map[string]interface{}{
		"id":    album.ID,
		"type":  "album",
		"title": domain.FoldUnicode(album.Title),
	}

	var artistNames []string
	for _, a := range album.Artists {
		artistNames = append(artistNames, domain.FoldUnicode(a.Name))
	}
	if len(artistNames) > 0 {
		doc["artist_names"] = artistNames
		doc["artist_name"] = artistNames[0]
	}

	return s.index.Index("album:"+album.ID, doc)
}

func (s *bleveSearchService) IndexArtist(ctx context.Context, artist *domain.Artist) error {
	doc := map[string]interface{}{
		"id":    artist.ID,
		"type":  "artist",
		"title": domain.FoldUnicode(artist.Name),
		"name":  domain.FoldUnicode(artist.Name),
	}
	return s.index.Index("artist:"+artist.ID, doc)
}

func (s *bleveSearchService) IndexPlaylist(ctx context.Context, playlist *domain.Playlist) error {
	doc := map[string]interface{}{
		"id":          playlist.ID,
		"type":        "playlist",
		"title":       domain.FoldUnicode(playlist.Name),
		"description": domain.FoldUnicode(playlist.Description),
	}
	return s.index.Index("playlist:"+playlist.ID, doc)
}

func (s *bleveSearchService) IndexComposer(ctx context.Context, composer *domain.Composer) error {
	doc := map[string]interface{}{
		"id":    composer.ID,
		"type":  "composer",
		"title": domain.FoldUnicode(composer.Name),
	}
	return s.index.Index("composer:"+composer.ID, doc)
}

func (s *bleveSearchService) Search(ctx context.Context, queryStr string) ([]domain.SearchResult, error) {
	// Fold query to match our folded index
	foldedQuery := domain.FoldUnicode(queryStr)
	cleanQuery := strings.ReplaceAll(foldedQuery, "*", "")
	terms := strings.Fields(cleanQuery)
	if len(terms) == 0 {
		return nil, nil
	}

	var termQueries []query.Query
	for _, term := range terms {
		lowerTerm := strings.ToLower(term)
		// For each term, match either exactly (via analyzer) or as a prefix
		termQuery := bleve.NewDisjunctionQuery(
			bleve.NewMatchQuery(lowerTerm),
			bleve.NewPrefixQuery(lowerTerm),
		)
		termQueries = append(termQueries, termQuery)
	}

	// AND logic for all terms
	conjunctionQuery := bleve.NewConjunctionQuery(termQueries...)

	// Boost exact phrase matches if there are multiple terms
	var finalQuery query.Query
	if len(terms) > 1 {
		phraseQuery := bleve.NewMatchPhraseQuery(strings.ToLower(cleanQuery))
		phraseQuery.SetBoost(2.0)
		finalQuery = bleve.NewDisjunctionQuery(phraseQuery, conjunctionQuery)
	} else {
		finalQuery = conjunctionQuery
	}

	searchRequest := bleve.NewSearchRequest(finalQuery)
	searchRequest.Fields = []string{"*"}
	searchRequest.Size = 200

	searchResults, err := s.index.Search(searchRequest)
	if err != nil {
		return nil, fmt.Errorf("failed to search: %w", err)
	}

	var results []domain.SearchResult
	for _, hit := range searchResults.Hits {
		typ := ""
		if v, ok := hit.Fields["type"]; ok {
			if s, ok := v.(string); ok {
				typ = s
			} else if sl, ok := v.([]interface{}); ok && len(sl) > 0 {
				typ = fmt.Sprintf("%v", sl[0])
			}
		}

		id := hit.ID
		if v, ok := hit.Fields["id"]; ok {
			if s, ok := v.(string); ok {
				id = s
			} else if sl, ok := v.([]interface{}); ok && len(sl) > 0 {
				id = fmt.Sprintf("%v", sl[0])
			}
		}

		results = append(results, domain.SearchResult{
			ID:    id,
			Type:  typ,
			Score: hit.Score,
		})
	}

	return results, nil
}

func (s *bleveSearchService) DeleteFromIndex(ctx context.Context, id string) error {
	_ = s.index.Delete(id)
	return s.index.Delete("track:" + id)
}

func (s *bleveSearchService) Close() error {
	return s.index.Close()
}
