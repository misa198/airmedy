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

	_ "github.com/blevesearch/bleve/v2/analysis/analyzer/custom"
	"github.com/blevesearch/bleve/v2/analysis/token/lowercase"
	_ "github.com/blevesearch/bleve/v2/analysis/tokenizer/unicode"
)

type bleveSearchService struct {
	indexPath string
	index     bleve.Index

	// syncBatch, when non-nil, accumulates documents from IndexTrack/Album/
	// Artist/Composer instead of committing each one individually. Only
	// touched from LibraryService's single sync-consumer goroutine (see
	// BeginSyncBatch/EndSyncBatch), so no locking is needed.
	syncBatch        *bleve.Batch
	syncBatchPending int
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

	return &bleveSearchService{indexPath: indexPath, index: index}, nil
}

func buildIndexMapping() mapping.IndexMapping {
	indexMapping := bleve.NewIndexMapping()

	// Register a custom analyzer that uses the unicode tokenizer and lowercase filter,
	// but does NOT remove English stop words (like "more", "than", "you", "the").
	err := indexMapping.AddCustomAnalyzer("no_stop_words", map[string]interface{}{
		"type":          "custom",
		"tokenizer":     "unicode",
		"token_filters": []string{lowercase.Name},
	})
	if err != nil {
		// Fallback to standard analyzer if custom registration fails
		panic(fmt.Sprintf("failed to register custom analyzer: %v", err))
	}

	// 1. Text field mapping for searchable content
	textFieldMapping := bleve.NewTextFieldMapping()
	textFieldMapping.Analyzer = "no_stop_words"
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

// --- Document builders (shared by single-doc Index* and BatchReindex) ---

func trackDoc(track *domain.TrackDTO) (string, map[string]interface{}) {
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

	return "track:" + track.ID, doc
}

func albumDoc(album *domain.AlbumDTO) (string, map[string]interface{}) {
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

	return "album:" + album.ID, doc
}

func artistDoc(artist *domain.Artist) (string, map[string]interface{}) {
	return "artist:" + artist.ID, map[string]interface{}{
		"id":    artist.ID,
		"type":  "artist",
		"title": domain.FoldUnicode(artist.Name),
		"name":  domain.FoldUnicode(artist.Name),
	}
}

func playlistDoc(playlist *domain.Playlist) (string, map[string]interface{}) {
	return "playlist:" + playlist.ID, map[string]interface{}{
		"id":          playlist.ID,
		"type":        "playlist",
		"title":       domain.FoldUnicode(playlist.Name),
		"description": domain.FoldUnicode(playlist.Description),
	}
}

func composerDoc(composer *domain.Composer) (string, map[string]interface{}) {
	return "composer:" + composer.ID, map[string]interface{}{
		"id":    composer.ID,
		"type":  "composer",
		"title": domain.FoldUnicode(composer.Name),
	}
}

func (s *bleveSearchService) IndexTrack(ctx context.Context, track *domain.TrackDTO) error {
	id, doc := trackDoc(track)
	return s.indexOrBatch(id, doc)
}

func (s *bleveSearchService) IndexAlbum(ctx context.Context, album *domain.AlbumDTO) error {
	id, doc := albumDoc(album)
	return s.indexOrBatch(id, doc)
}

func (s *bleveSearchService) IndexArtist(ctx context.Context, artist *domain.Artist) error {
	id, doc := artistDoc(artist)
	return s.indexOrBatch(id, doc)
}

func (s *bleveSearchService) IndexPlaylist(ctx context.Context, playlist *domain.Playlist) error {
	return s.index.Index(playlistDoc(playlist))
}

func (s *bleveSearchService) IndexComposer(ctx context.Context, composer *domain.Composer) error {
	id, doc := composerDoc(composer)
	return s.indexOrBatch(id, doc)
}

// indexOrBatch appends to the active sync batch if one is open (see
// BeginSyncBatch), auto-flushing at batchCommitSize; otherwise it commits
// the document immediately, same as before.
func (s *bleveSearchService) indexOrBatch(id string, doc map[string]interface{}) error {
	if s.syncBatch == nil {
		return s.index.Index(id, doc)
	}
	if err := s.syncBatch.Index(id, doc); err != nil {
		return fmt.Errorf("failed to add %q to sync batch: %w", id, err)
	}
	s.syncBatchPending++
	if s.syncBatchPending >= batchCommitSize {
		return s.flushSyncBatch()
	}
	return nil
}

func (s *bleveSearchService) flushSyncBatch() error {
	if s.syncBatchPending == 0 {
		return nil
	}
	if err := s.index.Batch(s.syncBatch); err != nil {
		return fmt.Errorf("failed to commit sync batch: %w", err)
	}
	s.syncBatch = s.index.NewBatch()
	s.syncBatchPending = 0
	return nil
}

// BeginSyncBatch switches IndexTrack/IndexAlbum/IndexArtist/IndexComposer
// into batch-accumulating mode for the duration of a bulk SyncFolder run.
func (s *bleveSearchService) BeginSyncBatch() {
	s.syncBatch = s.index.NewBatch()
	s.syncBatchPending = 0
}

// EndSyncBatch flushes any remaining batched documents and returns to
// per-document indexing. Must be called (e.g. via defer) after
// BeginSyncBatch, on every return path including errors/cancellation.
func (s *bleveSearchService) EndSyncBatch() error {
	err := s.flushSyncBatch()
	s.syncBatch = nil
	s.syncBatchPending = 0
	return err
}

// batchCommitSize bounds how many documents accumulate before a single
// fsync-ing commit. Keeps memory bounded and gives steady progress while
// avoiding the per-document fsync that makes full reindex hang on Windows.
const batchCommitSize = 500

func (s *bleveSearchService) BatchReindex(ctx context.Context, data *domain.ReindexData, onProgress func(path string)) error {
	if data == nil {
		return nil
	}

	batch := s.index.NewBatch()
	pending := 0

	flush := func() error {
		if pending == 0 {
			return nil
		}
		if err := s.index.Batch(batch); err != nil {
			return fmt.Errorf("failed to commit reindex batch: %w", err)
		}
		batch = s.index.NewBatch()
		pending = 0
		return nil
	}

	add := func(id string, doc map[string]interface{}, label string) error {
		if err := ctx.Err(); err != nil {
			return err
		}
		if err := batch.Index(id, doc); err != nil {
			return fmt.Errorf("failed to add %q to reindex batch: %w", id, err)
		}
		pending++
		if onProgress != nil {
			onProgress(label)
		}
		if pending >= batchCommitSize {
			return flush()
		}
		return nil
	}

	for _, t := range data.Tracks {
		id, doc := trackDoc(t)
		if err := add(id, doc, "Track: "+t.Title); err != nil {
			return err
		}
	}
	for _, a := range data.Albums {
		id, doc := albumDoc(a)
		if err := add(id, doc, "Album: "+a.Title); err != nil {
			return err
		}
	}
	for _, ar := range data.Artists {
		id, doc := artistDoc(ar)
		if err := add(id, doc, "Artist: "+ar.Name); err != nil {
			return err
		}
	}
	for _, c := range data.Composers {
		id, doc := composerDoc(c)
		if err := add(id, doc, "Composer: "+c.Name); err != nil {
			return err
		}
	}
	for _, p := range data.Playlists {
		id, doc := playlistDoc(p)
		if err := add(id, doc, "Playlist: "+p.Name); err != nil {
			return err
		}
	}

	return flush()
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

// DeleteTracksFromIndex removes many track documents in batched commits
// instead of one fsync-ing commit per document — looping DeleteFromIndex for
// a large folder is the same "hang on Windows" cost BatchReindex/
// BeginSyncBatch already avoid for imports (batchCommitSize), so bulk
// removal gets the same treatment.
func (s *bleveSearchService) DeleteTracksFromIndex(ctx context.Context, ids []string) error {
	if len(ids) == 0 {
		return nil
	}

	batch := s.index.NewBatch()
	pending := 0
	flush := func() error {
		if pending == 0 {
			return nil
		}
		if err := s.index.Batch(batch); err != nil {
			return fmt.Errorf("failed to commit delete batch: %w", err)
		}
		batch = s.index.NewBatch()
		pending = 0
		return nil
	}

	for _, id := range ids {
		batch.Delete(id)
		batch.Delete("track:" + id)
		pending++
		if pending >= batchCommitSize {
			if err := flush(); err != nil {
				return err
			}
		}
	}
	return flush()
}

// BatchDeleteFromIndex removes arbitrary already-prefixed doc IDs in batched
// commits — same fsync-per-doc cost as DeleteTracksFromIndex avoids, but for
// callers (orphan cleanup) that delete a mix of album/artist/composer docs.
func (s *bleveSearchService) BatchDeleteFromIndex(ctx context.Context, docIDs []string) error {
	if len(docIDs) == 0 {
		return nil
	}

	batch := s.index.NewBatch()
	pending := 0
	flush := func() error {
		if pending == 0 {
			return nil
		}
		if err := s.index.Batch(batch); err != nil {
			return fmt.Errorf("failed to commit delete batch: %w", err)
		}
		batch = s.index.NewBatch()
		pending = 0
		return nil
	}

	for _, id := range docIDs {
		batch.Delete(id)
		pending++
		if pending >= batchCommitSize {
			if err := flush(); err != nil {
				return err
			}
		}
	}
	return flush()
}

func (s *bleveSearchService) DeleteAlbumFromIndex(ctx context.Context, albumID string) error {
	return s.index.Delete("album:" + albumID)
}

func (s *bleveSearchService) DeleteArtistFromIndex(ctx context.Context, artistID string) error {
	return s.index.Delete("artist:" + artistID)
}

func (s *bleveSearchService) DeleteComposerFromIndex(ctx context.Context, composerID string) error {
	return s.index.Delete("composer:" + composerID)
}

func (s *bleveSearchService) DocCount(ctx context.Context) (uint64, error) {
	return s.index.DocCount()
}

func (s *bleveSearchService) Reset(ctx context.Context) error {
	if err := s.index.Close(); err != nil {
		return fmt.Errorf("failed to close search index for reset: %w", err)
	}

	if err := os.RemoveAll(s.indexPath); err != nil {
		return fmt.Errorf("failed to delete search index files: %w", err)
	}

	indexMapping := buildIndexMapping()
	index, err := bleve.New(s.indexPath, indexMapping)
	if err != nil {
		return fmt.Errorf("failed to recreate search index: %w", err)
	}
	s.index = index
	return nil
}

func (s *bleveSearchService) Close() error {
	return s.index.Close()
}
