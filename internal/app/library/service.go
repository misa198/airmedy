package library

import (
	"context"
	"encoding/json"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"airmedy/internal/app/lyrics"
	"airmedy/internal/domain"
	"airmedy/internal/infra/artwork"

	"github.com/google/uuid"
	"github.com/wailsapp/wails/v3/pkg/application"
)

// backgroundSyncKey marks a context as belonging to an automatic (periodic)
// sync. Such syncs suppress the sync-progress UI events (started/progress/
// finished) so the progress dialog only appears for user-initiated syncs; the
// data-refresh events (library:updated, track-updated/deleted) still fire so the
// UI silently reflects newly discovered changes.
type ctxKey int

const backgroundSyncKey ctxKey = iota

// withBackgroundSync returns a context flagged as an automatic background sync.
func withBackgroundSync(ctx context.Context) context.Context {
	return context.WithValue(ctx, backgroundSyncKey, true)
}

// isBackgroundSync reports whether ctx belongs to an automatic background sync.
func isBackgroundSync(ctx context.Context) bool {
	v, _ := ctx.Value(backgroundSyncKey).(bool)
	return v
}

// SupportedAudioExtensions is the set of file extensions the library accepts.
var SupportedAudioExtensions = map[string]bool{
	".mp3":  true,
	".flac": true,
	".m4a":  true,
	".wav":  true,
	".ogg":  true,
	".opus": true,
	".aiff": true,
	".aif":  true,
	".ape":  true,
	".wv":   true,
	".dsf":  true,
	".dff":  true,
}

type LibraryService struct {
	trackRepo         domain.TrackRepository
	albumRepo         domain.AlbumRepository
	artistRepo        domain.ArtistRepository
	genreRepo         domain.GenreRepository
	composerRepo      domain.ComposerRepository
	playlistRepo      domain.PlaylistRepository
	watchedFolderRepo domain.WatchedFolderRepository
	settingsRepo      domain.SettingsRepository
	syncStateRepo     domain.LibrarySyncStateRepository
	metadataExtractor domain.MetadataExtractor
	metadataWriter    domain.MetadataWriter
	artworkCache      domain.ArtworkCache
	searchService     domain.SearchService
	txManager         domain.TxManager
	lyricsService     *lyrics.LyricsService
	logger            *slog.Logger

	trackUpdateListeners    []func(*domain.TrackDTO)
	favoriteChangeListeners []func(*domain.TrackDTO)
	analysisListeners       []func(string)
	trackDeletedListeners   []func([]string)
	syncFinishedListeners   []func()
	artistArtworkQueue      chan artistArtworkJob
	pendingArtistArtwork    map[string]struct{}
	pendingArtistArtworkMu  sync.Mutex
	artistArtworkLocks      sync.Map         // artistID -> *sync.Mutex; serializes artwork writes
	bulkSyncMu              sync.Mutex       // serializes SyncFolder/ResplitLibrary/ReindexAll: all three read/write the shared syncEntityCache and search index sync-batch below, which are not safe for concurrent use
	syncing                 atomic.Int32     // >0 while a bulk SyncFolder runs (gates per-track work)
	syncEntityCache         *syncEntityCache // non-nil only during a SyncFolder/ResplitLibrary run; caches resolved entity IDs
	syncReschedule          chan struct{}    // signals the periodic-sync scheduler to re-read the interval setting
	ctx                     context.Context
	cancel                  context.CancelFunc
	mu                      sync.RWMutex
}

type artistArtworkJob struct {
	ArtistID string
	EventID  string
}

func NewLibraryService(
	trackRepo domain.TrackRepository,
	albumRepo domain.AlbumRepository,
	artistRepo domain.ArtistRepository,
	genreRepo domain.GenreRepository,
	composerRepo domain.ComposerRepository,
	playlistRepo domain.PlaylistRepository,
	watchedFolderRepo domain.WatchedFolderRepository,
	settingsRepo domain.SettingsRepository,
	syncStateRepo domain.LibrarySyncStateRepository,
	metadataExtractor domain.MetadataExtractor,
	metadataWriter domain.MetadataWriter,
	artworkCache domain.ArtworkCache,
	searchService domain.SearchService,
	txManager domain.TxManager,
	lyricsService *lyrics.LyricsService,
	logger *slog.Logger,
) (*LibraryService, error) {
	ctx, cancel := context.WithCancel(context.Background())

	return &LibraryService{
		trackRepo:            trackRepo,
		albumRepo:            albumRepo,
		artistRepo:           artistRepo,
		genreRepo:            genreRepo,
		composerRepo:         composerRepo,
		playlistRepo:         playlistRepo,
		watchedFolderRepo:    watchedFolderRepo,
		settingsRepo:         settingsRepo,
		syncStateRepo:        syncStateRepo,
		metadataExtractor:    metadataExtractor,
		metadataWriter:       metadataWriter,
		artworkCache:         artworkCache,
		searchService:        searchService,
		txManager:            txManager,
		lyricsService:        lyricsService,
		logger:               logger.With("module", "library"),
		artistArtworkQueue:   make(chan artistArtworkJob, 100),
		pendingArtistArtwork: make(map[string]struct{}),
		syncReschedule:       make(chan struct{}, 1),
		ctx:                  ctx,
		cancel:               cancel,
	}, nil
}

func (s *LibraryService) AddTrackUpdateListener(l func(*domain.TrackDTO)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.trackUpdateListeners = append(s.trackUpdateListeners, l)
}

func (s *LibraryService) notifyTrackUpdated(track *domain.TrackDTO) {
	s.mu.RLock()
	listeners := make([]func(*domain.TrackDTO), len(s.trackUpdateListeners))
	copy(listeners, s.trackUpdateListeners)
	s.mu.RUnlock()

	for _, l := range listeners {
		l(track)
	}
}

// AddFavoriteChangeListener registers a callback fired only when a track's
// favorite state actually changes (ToggleFavorite). Deliberately separate from
// trackUpdateListeners, which also fires on import and metadata edits — those
// must not sync a favorite state to Last.fm (a fresh import would otherwise
// fire a track.unlove for every non-favorite track).
func (s *LibraryService) AddFavoriteChangeListener(l func(*domain.TrackDTO)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.favoriteChangeListeners = append(s.favoriteChangeListeners, l)
}

func (s *LibraryService) notifyFavoriteChanged(track *domain.TrackDTO) {
	s.mu.RLock()
	listeners := make([]func(*domain.TrackDTO), len(s.favoriteChangeListeners))
	copy(listeners, s.favoriteChangeListeners)
	s.mu.RUnlock()

	for _, l := range listeners {
		l(track)
	}
}

// AddAnalysisListener registers a callback fired with the track ID whenever a
// track is freshly imported (covers both SyncFolder and single-file
// ImportFile). Deliberately separate from trackUpdateListeners, which also
// fires on ToggleFavorite/metadata edits — those must not trigger
// re-analysis since DSP features don't change.
func (s *LibraryService) AddAnalysisListener(l func(string)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.analysisListeners = append(s.analysisListeners, l)
}

func (s *LibraryService) notifyAnalysisPending(trackID string) {
	s.mu.RLock()
	listeners := make([]func(string), len(s.analysisListeners))
	copy(listeners, s.analysisListeners)
	s.mu.RUnlock()

	for _, l := range listeners {
		l(trackID)
	}
}

// AddTrackDeletedListener registers a callback fired with the IDs of tracks
// that were just removed (single-file delete, directory delete, or
// RemoveWatchedFolder). Lets the analysis pool drop them from its queue
// immediately instead of wastefully analyzing/writing features for tracks
// that no longer exist — otherwise a deletion racing a large in-flight
// analysis backlog (e.g. right after a big import) keeps competing with the
// delete for the same DB writer for no reason.
func (s *LibraryService) AddTrackDeletedListener(l func([]string)) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.trackDeletedListeners = append(s.trackDeletedListeners, l)
}

func (s *LibraryService) notifyTracksDeleted(ids []string) {
	if len(ids) == 0 {
		return
	}
	s.mu.RLock()
	listeners := make([]func([]string), len(s.trackDeletedListeners))
	copy(listeners, s.trackDeletedListeners)
	s.mu.RUnlock()

	for _, l := range listeners {
		l(ids)
	}
}

// AddSyncFinishedListener registers a callback fired once a SyncFolder run
// completes. Lets the analysis pipeline trigger a percentile recompute right
// after a bulk import instead of waiting on its batch-size/debounce
// triggers, which can leave a small library's mood scores unpopulated for a
// while after a fresh scan.
func (s *LibraryService) AddSyncFinishedListener(l func()) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.syncFinishedListeners = append(s.syncFinishedListeners, l)
}

func (s *LibraryService) notifySyncFinished() {
	s.mu.RLock()
	listeners := make([]func(), len(s.syncFinishedListeners))
	copy(listeners, s.syncFinishedListeners)
	s.mu.RUnlock()

	for _, l := range listeners {
		func() {
			defer func() {
				if r := recover(); r != nil {
					s.logger.Error("sync-finished listener panicked", "panic", r)
				}
			}()
			l()
		}()
	}
}

func (s *LibraryService) Start(ctx context.Context) error {
	// Clear any albums/artists/genres/composers stranded by a previous session.
	s.cleanupOrphanedEntities(ctx)

	// Rescan watched folders on a timer instead of a real-time file watcher.
	// A kqueue-based watcher (macOS) needs one open fd per watched file, which
	// exhausts the process fd limit on large libraries; periodic SyncFolder
	// reconciles added/changed/removed files just as well, only less promptly.
	go s.runSyncScheduler(s.ctx)
	go s.StartArtistArtworkWorker(s.ctx)
	go s.maybeRescanArtistImages(s.ctx)

	// If the search index is completely empty but the library has tracks,
	// automatically trigger a full re-indexing of the search index in the background.
	go func() {
		time.Sleep(2 * time.Second)
		trackCount, err := s.trackRepo.Count(s.ctx)
		if err == nil && trackCount > 0 {
			docCount, err := s.searchService.DocCount(s.ctx)
			if err == nil && docCount == 0 {
				s.logger.Info("Search index is empty but database has tracks. Rebuilding search index...")
				if err := s.ReindexAll(s.ctx); err != nil {
					s.logger.Warn("Failed to rebuild search index on startup", "error", err)
				}
			}
		}
	}()

	return nil
}

func (s *LibraryService) Stop(ctx context.Context) error {
	s.cancel()
	return nil
}

// GetSettings exposes the persisted app settings (used by the Wails layer to
// resolve artist artwork display preferences).
func (s *LibraryService) GetSettings(ctx context.Context) (*domain.AppSettings, error) {
	return s.settingsRepo.Load(ctx)
}

// CurrentSyncInterval reads the configured library sync interval, falling back
// to the default if settings can't be loaded.
func (s *LibraryService) CurrentSyncInterval() string {
	settings, err := s.settingsRepo.Load(s.ctx)
	if err != nil || settings.LibrarySyncInterval == "" {
		return domain.DefaultSyncInterval
	}
	return settings.LibrarySyncInterval
}

// RescheduleSync tells the periodic-sync scheduler to re-read the interval
// setting immediately (e.g. after the user changes it). Non-blocking: a pending
// signal is coalesced.
func (s *LibraryService) RescheduleSync() {
	select {
	case s.syncReschedule <- struct{}{}:
	default:
	}
}

// runSyncScheduler periodically rescans all watched folders so external file
// changes are picked up without a real-time file watcher. It replaces the
// fsnotify watcher, which on macOS (kqueue) holds one fd per watched file and
// exhausts the process fd limit on large libraries.
//
// Behavior by interval setting:
//   - "manual": never auto-scans (user triggers Sync Library).
//   - "launch": scans once at startup only.
//   - "15m"/"30m"/"1h": scans once at startup, then repeats every interval.
//
// A RescheduleSync signal makes the loop re-read the setting right away.
func (s *LibraryService) runSyncScheduler(ctx context.Context) {
	// Give startup (search reindex, analysis backfill) a moment before the
	// first scan so it doesn't contend with boot work.
	select {
	case <-ctx.Done():
		return
	case <-time.After(5 * time.Second):
	}

	for {
		interval := s.CurrentSyncInterval()

		if interval != domain.SyncIntervalManual {
			// Background sync: suppress the progress dialog (see isBackgroundSync).
			if err := s.SyncLibrary(withBackgroundSync(ctx)); err != nil && ctx.Err() == nil {
				s.logger.Warn("periodic library sync failed", "error", err)
			}
		}

		period, repeats := domain.SyncIntervalDuration(interval)

		var timer *time.Timer
		var timerC <-chan time.Time
		if repeats {
			timer = time.NewTimer(period)
			timerC = timer.C
		}

		select {
		case <-ctx.Done():
			if timer != nil {
				timer.Stop()
			}
			return
		case <-s.syncReschedule:
			// Setting changed: stop the current timer and loop to re-read it.
			if timer != nil {
				timer.Stop()
			}
		case <-timerC:
			// Interval elapsed: loop back to scan again.
		}
	}
}

// cleanupOrphanedEntities removes albums, artists, composers and genres that no
// longer reference any track. Errors are logged, not returned, since this is a
// best-effort cleanup invoked after deletions.
// cleanupOrphanedEntities removes albums/artists/composers/genres that no longer
// have any tracks, keeping the DB and the search index in sync. Genres aren't
// indexed, so only their rows are dropped.
func (s *LibraryService) cleanupOrphanedEntities(ctx context.Context) {
	// Collect every orphaned doc ID across all three indexed entity types and
	// remove them in one batched commit instead of one fsync-ing
	// DeleteXFromIndex call per entity — a bulk removal (e.g. a folder with
	// many distinct albums/artists) can orphan hundreds of rows at once.
	var docIDs []string

	if ids, err := s.albumRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned albums", "error", err)
	} else {
		for _, id := range ids {
			docIDs = append(docIDs, "album:"+id)
		}
	}
	if ids, err := s.artistRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned artists", "error", err)
	} else {
		for _, id := range ids {
			docIDs = append(docIDs, "artist:"+id)
		}
	}
	if ids, err := s.composerRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned composers", "error", err)
	} else {
		for _, id := range ids {
			docIDs = append(docIDs, "composer:"+id)
		}
	}
	if _, err := s.genreRepo.DeleteOrphaned(ctx); err != nil {
		s.logger.Warn("Failed to delete orphaned genres", "error", err)
	}

	if err := s.searchService.BatchDeleteFromIndex(ctx, docIDs); err != nil {
		s.logger.Warn("Failed to delete orphaned entities from index", "count", len(docIDs), "error", err)
	}
}

func (s *LibraryService) AddWatchedFolder(ctx context.Context, path string) error {
	path = filepath.Clean(path)
	s.logger.Info("Adding watched folder", "path", path)

	// Check for parent/child relationships
	existing, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		return fmt.Errorf("failed to get existing watched folders: %w", err)
	}

	for _, f := range existing {
		if f.Path == path {
			return fmt.Errorf("folder already watched: %s", path)
		}

		// If new path is child of existing
		if isSubPath(f.Path, path) {
			return fmt.Errorf("folder is already covered by watched parent: %s", f.Path)
		}

		// If new path is parent of existing
		if isSubPath(path, f.Path) {
			s.logger.Info("New folder covers existing watched folder, removing child", "child", f.Path, "parent", path)
			if err := s.RemoveWatchedFolder(ctx, f.ID, true); err != nil {
				s.logger.Warn("Failed to remove child folder", "path", f.Path, "error", err)
			}
		}
	}

	folder := &domain.WatchedFolder{
		ID:        uuid.New().String(),
		Path:      path,
		CreatedAt: time.Now(),
	}

	if err := s.watchedFolderRepo.Save(ctx, folder); err != nil {
		return fmt.Errorf("failed to save watched folder: %w", err)
	}

	// Trigger initial sync in a goroutine. Subsequent changes are picked up by
	// the periodic sync scheduler (see runSyncScheduler).
	go func() {
		if err := s.SyncFolder(context.Background(), path); err != nil {
			s.logger.Error("Failed to sync folder", "path", path, "error", err)
		}
	}()

	return nil
}

func (s *LibraryService) RemoveWatchedFolder(ctx context.Context, id string, keepTracks bool) error {
	folder, err := s.watchedFolderRepo.GetByID(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to get watched folder: %w", err)
	}
	if folder == nil {
		return nil
	}

	start := time.Now()
	s.logger.Info("Removing watched folder", "path", folder.Path, "keepTracks", keepTracks)

	var step time.Time

	if !keepTracks {
		// 2. Get tracks for this folder to remove from search index. Notify
		//    analysis listeners first so a running analysis pool drops these
		//    IDs from its queue right away instead of continuing to burn CPU
		//    and DB writes racing the deletion below (worst when this runs
		//    right after a big import, while the pool is still backfilling).
		step = time.Now()
		tracks, err := s.trackRepo.GetByPathPrefix(ctx, folder.Path)
		s.logger.Debug("RemoveWatchedFolder: GetByPathPrefix done", "path", folder.Path, "count", len(tracks), "took", time.Since(step), "error", err)
		if err == nil {
			ids := make([]string, len(tracks))
			for i, track := range tracks {
				ids[i] = track.ID
			}

			step = time.Now()
			s.notifyTracksDeleted(ids)
			s.logger.Debug("RemoveWatchedFolder: notifyTracksDeleted done", "count", len(ids), "took", time.Since(step))

			// Batched: one (or a few, at batchCommitSize) commits instead of
			// two fsync-ing commits per track — the per-track loop this
			// replaced is what made removing a large folder slow.
			step = time.Now()
			if err := s.searchService.DeleteTracksFromIndex(ctx, ids); err != nil {
				s.logger.Warn("Failed to delete tracks from search index", "count", len(ids), "error", err)
			}
			s.logger.Debug("RemoveWatchedFolder: DeleteTracksFromIndex done", "count", len(ids), "took", time.Since(step))
		}

		// 3. Delete tracks from DB
		step = time.Now()
		if err := s.trackRepo.DeleteByPathPrefix(ctx, folder.Path); err != nil {
			return fmt.Errorf("failed to delete tracks from DB: %w", err)
		}
		s.logger.Debug("RemoveWatchedFolder: DeleteByPathPrefix done", "path", folder.Path, "took", time.Since(step))

		// 4. Cleanup orphaned entities
		step = time.Now()
		s.cleanupOrphanedEntities(ctx)
		s.logger.Debug("RemoveWatchedFolder: cleanupOrphanedEntities done", "took", time.Since(step))

		// 5. Cleanup orphaned artworks
		step = time.Now()
		if err := s.CleanupOrphanedArtworks(ctx); err != nil {
			s.logger.Warn("Failed to cleanup orphaned artworks", "error", err)
		}
		s.logger.Debug("RemoveWatchedFolder: CleanupOrphanedArtworks done", "took", time.Since(step))
	}

	// 6. Delete watched folder record
	step = time.Now()
	if err := s.watchedFolderRepo.Delete(ctx, id); err != nil {
		return fmt.Errorf("failed to delete watched folder record: %w", err)
	}
	s.logger.Debug("RemoveWatchedFolder: folder record deleted", "took", time.Since(step))

	// 7. Notify frontend
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:updated", nil)
	}

	s.logger.Info("Removed watched folder", "path", folder.Path, "totalTook", time.Since(start))

	return nil
}

func (s *LibraryService) CleanupOrphanedArtworks(ctx context.Context) error {
	keys, err := s.trackRepo.GetAllArtworkKeys(ctx)
	if err != nil {
		return err
	}

	activeKeys := make(map[string]bool)
	for _, k := range keys {
		activeKeys[k] = true
	}

	// Include artist artwork (all sources) so it isn't deleted while the artist
	// exists — and so all of an artist's images are removed once it's orphaned.
	artistKeys, err := s.artistRepo.GetAllArtworkKeys(ctx)
	if err != nil {
		return err
	}
	for _, k := range artistKeys {
		activeKeys[k] = true
	}

	return s.artworkCache.CleanupOrphaned(ctx, activeKeys)
}

// SyncFolder syncs a single folder. See the bulkSyncMu comment on
// syncFolderLocked: this locks for the duration of one standalone call. Do not
// call this from inside SyncLibrary/ResplitLibrary/ReindexAll — they already
// hold the lock for their whole run and calling this would self-deadlock; call
// syncFolderLocked directly instead.
func (s *LibraryService) SyncFolder(ctx context.Context, root string) error {
	s.bulkSyncMu.Lock()
	defer s.bulkSyncMu.Unlock()
	return s.syncFolderLocked(ctx, root)
}

func (s *LibraryService) syncFolderLocked(ctx context.Context, root string) error {
	s.logger.Info("Starting folder sync", "root", root)

	// Mark a bulk sync in progress so per-file ImportFile skips its own artist
	// image lookup — the batch pass below handles it far more cheaply.
	s.syncing.Add(1)
	defer s.syncing.Add(-1)

	// Cache resolved artist/album/genre/composer IDs for the duration of this
	// run so repeated entities (e.g. one artist across 200 tracks) don't hit
	// GetByNormalizationKey again. Only the consumer loop below touches it, so
	// no locking is needed. nil outside a sync run (see resolveEntities).
	s.syncEntityCache = newSyncEntityCache()
	defer func() { s.syncEntityCache = nil }()

	// Batch search-index writes instead of committing one fsync per document.
	s.searchService.BeginSyncBatch()
	defer func() {
		if err := s.searchService.EndSyncBatch(); err != nil {
			s.logger.Warn("Failed to flush search index sync batch", "error", err)
		}
	}()

	// Was the library empty before this sync? Combined with a missing signature
	// below, this distinguishes a fresh install (baseline it silently) from an
	// upgrade with pre-existing tracks (leave unset so the user is told to resync).
	priorCount, _ := s.trackRepo.Count(ctx)

	supportedExtensions := SupportedAudioExtensions

	// 1. Count files
	var total int
	_ = filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err == nil && !d.IsDir() {
			filename := filepath.Base(path)
			if strings.HasPrefix(filename, ".") {
				return nil
			}

			ext := strings.ToLower(filepath.Ext(path))
			if supportedExtensions[ext] {
				total++
			}
		}
		return nil
	})

	if app := application.Get(); app != nil && app.Event != nil && !isBackgroundSync(ctx) {
		app.Event.Emit("library:sync-started", map[string]interface{}{
			"path":  root,
			"total": total,
		})
	}

	// 2. Import files.
	//
	// Parse files in parallel (CPU/disk bound) but funnel every DB write through a
	// single consumer goroutine, so SQLite never sees concurrent writers. The
	// unchanged-file skip check is done against an in-memory map preloaded once
	// (instead of a per-file DB read), keeping the parse workers DB-free.
	type fileStamp struct {
		size  int64
		mtime int64
	}
	knownStamps := make(map[string]fileStamp)
	schemaVersion, err := s.syncStateRepo.GetMetadataSchemaVersion(ctx)
	if err != nil {
		s.logger.Warn("Failed to load metadata schema version", "error", err)
	}
	if schemaVersion >= currentMetadataSchemaVersion {
		if existing, err := s.trackRepo.GetByPathPrefix(ctx, root); err == nil {
			for _, t := range existing {
				knownStamps[filepath.Clean(t.Path)] = fileStamp{size: t.FileSize, mtime: t.Mtime.Unix()}
			}
		} else {
			s.logger.Warn("Failed to preload existing tracks for sync", "root", root, "error", err)
		}
	} else {
		s.logger.Info("Metadata schema version changed; re-parsing all existing tracks", "root", root)
	}

	// Load delimiter settings once; shared (read-only) by all parse workers.
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return fmt.Errorf("failed to load settings: %w", err)
	}

	foundPaths := make(map[string]bool)
	imageDirs := make(map[string]bool) // directories containing an artist.{jpg,jpeg,png}
	var toImport []string
	walkErr := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			s.logger.Warn("Error walking path", "path", path, "error", err)
			return nil
		}

		filename := filepath.Base(path)
		if strings.HasPrefix(filename, ".") {
			return nil
		}

		if d.IsDir() {
			return nil
		}

		if isArtistImageFile(filename) {
			imageDirs[filepath.Dir(path)] = true
			return nil
		}

		ext := strings.ToLower(filepath.Ext(path))
		if !supportedExtensions[ext] {
			return nil
		}

		clean := filepath.Clean(path)
		foundPaths[clean] = true

		// Optimization: skip files unchanged since last sync (in-memory check).
		if info, err := d.Info(); err == nil {
			if st, ok := knownStamps[clean]; ok && st.size == info.Size() && st.mtime == info.ModTime().Unix() {
				return nil
			}
		}

		toImport = append(toImport, path)
		return nil
	})
	if walkErr != nil {
		return fmt.Errorf("failed to walk directory: %w", walkErr)
	}

	// Parallel parse → single-writer persist.
	workers := min(runtime.NumCPU(), 8)
	if workers < 1 {
		workers = 1
	}
	paths := make(chan string)
	jobs := make(chan *importJob)

	var wg sync.WaitGroup
	for i := 0; i < workers; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for path := range paths {
				if ctx.Err() != nil {
					return
				}
				job, err := s.parseFile(ctx, path, settings)
				if err != nil {
					s.logger.Error("Failed to parse file", "path", path, "error", err)
					continue
				}
				select {
				case jobs <- job:
				case <-ctx.Done():
					return
				}
			}
		}()
	}

	// Feed paths to the pool, then close once drained.
	go func() {
		defer close(paths)
		for _, path := range toImport {
			select {
			case paths <- path:
			case <-ctx.Done():
				return
			}
		}
	}()

	// Close jobs once all workers are done.
	go func() {
		wg.Wait()
		close(jobs)
	}()

	// Single consumer: serialize all DB writes and emit progress. Start the counter
	// past the files skipped as unchanged so the bar still advances to Total.
	current := total - len(toImport)
	emitProgress := !isBackgroundSync(ctx) // background syncs run without the dialog
	for job := range jobs {
		current++
		if app := application.Get(); emitProgress && app != nil && app.Event != nil {
			app.Event.Emit("library:sync-progress", domain.SyncProgress{
				Current: current,
				Total:   total,
				Path:    job.path,
			})
		}
		if err := s.persistImported(ctx, job); err != nil {
			s.logger.Error("Failed to import file", "path", job.path, "error", err)
		}
	}

	// 2b. Apply local artist images in one batch (per image directory).
	s.applyLocalArtistImagesForDirs(ctx, imageDirs)

	// 3. Cleanup missing files
	s.logger.Info("Cleaning up missing files", "root", root)
	var deletedIDs []string
	existingTracks, err := s.trackRepo.GetByPathPrefix(ctx, root)
	if err == nil {
		// Batched: all deletes share one transaction/fsync instead of one
		// commit per track — same fix as RemoveWatchedFolder. A resync of a
		// folder with many moved/deleted files was hitting this the same way.
		txErr := s.txManager.RunInTx(ctx, func(ctx context.Context) error {
			for _, t := range existingTracks {
				if foundPaths[filepath.Clean(t.Path)] {
					continue
				}
				s.logger.Info("Removing missing track", "path", t.Path)
				if err := s.trackRepo.Delete(ctx, t.ID); err != nil {
					s.logger.Warn("Failed to delete missing track from DB", "path", t.Path, "error", err)
					continue
				}
				deletedIDs = append(deletedIDs, t.ID)
			}
			return nil
		})
		if txErr != nil {
			s.logger.Warn("Failed to delete missing tracks from DB", "error", txErr)
		}
		if err := s.searchService.DeleteTracksFromIndex(ctx, deletedIDs); err != nil {
			s.logger.Warn("Failed to delete missing tracks from search index", "count", len(deletedIDs), "error", err)
		}
	}

	// Tell the analysis pool to drop these before it wastes cycles/DB writes
	// analyzing tracks that no longer exist.
	s.notifyTracksDeleted(deletedIDs)

	// Always clear orphans: a track removed here (or by an earlier run/path) can
	// leave an album/artist/genre/composer with no tracks.
	s.cleanupOrphanedEntities(ctx)
	if err := s.CleanupOrphanedArtworks(ctx); err != nil {
		s.logger.Warn("Failed to cleanup orphaned artworks", "error", err)
	}

	// Baseline the delimiters signature on a fresh install's first sync, so a
	// just-populated library is not flagged as "pending resync". Only when there
	// was no prior data and no prior signature — an upgrade (priorCount > 0) keeps
	// the empty signature so the user is prompted to resync against new delimiters.
	if priorCount == 0 {
		if stored, err := s.syncStateRepo.GetDelimitersSignature(ctx); err == nil && stored == "" {
			if err := s.syncStateRepo.SetDelimitersSignature(ctx, delimitersSignature(settings)); err != nil {
				s.logger.Warn("Failed to baseline delimiters signature", "error", err)
			}
		}
	}

	s.logger.Info("Finished folder sync", "root", root)
	if app := application.Get(); app != nil && app.Event != nil {
		for _, id := range deletedIDs {
			app.Event.Emit("library:track-deleted", id)
		}
		if !isBackgroundSync(ctx) {
			app.Event.Emit("library:sync-finished", root)
		}
	}
	s.notifySyncFinished()
	return nil
}

// importJob carries everything parseFile gathers from disk for a single track so
// persistImported can write it to the DB without touching the filesystem. This
// split lets SyncFolder parse files in parallel (CPU/disk bound) while serializing
// all DB writes through a single goroutine.
type importJob struct {
	path       string
	dto        *domain.TrackDTO
	metaLyrics string
	lyricsSrc  string // "meta-plain" | "meta-synced" | "" (none)
}

// ImportFile imports (or re-imports) a single track. Used by the file watcher and
// any single-file path. It runs the parse phase then the DB-write phase inline.
func (s *LibraryService) ImportFile(ctx context.Context, path string) error {
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return fmt.Errorf("failed to load settings for %s: %w", path, err)
	}
	job, err := s.parseFile(ctx, path, settings)
	if err != nil {
		return err
	}
	return s.persistImported(ctx, job)
}

// parseFile reads a track from disk and produces an importJob. It performs only
// file I/O and pure CPU work (metadata decode, delimiter split, artwork save) and
// MUST NOT touch the database, so it is safe to call concurrently from a worker
// pool. settings is passed in (loaded once by the caller) instead of re-loaded.
func (s *LibraryService) parseFile(ctx context.Context, path string, settings *domain.AppSettings) (*importJob, error) {
	info, err := os.Stat(path)
	if err != nil {
		return nil, fmt.Errorf("failed to stat file %s: %w", path, err)
	}

	dto, err := s.metadataExtractor.Extract(ctx, path)
	if err != nil {
		return nil, fmt.Errorf("failed to extract metadata from %s: %w", path, err)
	}

	dto.FileSize = info.Size()
	dto.Mtime = info.ModTime()

	// Split raw tag values into individual entities using the user-configured
	// delimiters (single source of truth, shared with resync).
	s.buildEntitiesFromRaw(dto, settings)

	// Extract artwork if available. artworkCache.Save is content-hash keyed and
	// stateless, so concurrent calls are safe.
	artworkData, mimeType, err := s.metadataExtractor.ExtractArtwork(ctx, path)
	if err == nil && artworkData != nil {
		s.logger.Debug("Artwork extracted", "path", path, "size", len(artworkData), "mime", mimeType)
		key, err := s.artworkCache.Save(ctx, artworkData, mimeType)
		if err != nil {
			s.logger.Warn("Failed to save artwork", "path", path, "error", err)
		} else {
			s.logger.Debug("Artwork saved", "path", path, "key", key)
			dto.ArtworkKey = key
			dto.Album.ArtworkKey = key
		}
	} else if err != nil {
		s.logger.Debug("Error extracting artwork", "path", path, "error", err)
	} else {
		s.logger.Debug("No artwork found in file", "path", path)
	}

	job := &importJob{path: path, dto: dto}

	// Extract lyrics from metadata (file read only; the DB write happens later in
	// persistImported once the track ID is resolved).
	if s.lyricsService != nil {
		if metaLyrics, isSynced, err := s.metadataExtractor.ExtractLyrics(ctx, path); err == nil && metaLyrics != "" {
			job.metaLyrics = metaLyrics
			job.lyricsSrc = "meta-plain"
			if isSynced {
				job.lyricsSrc = "meta-synced"
			}
		}
	}

	return job, nil
}

// persistImported writes a parsed importJob to the database and search index, and
// notifies listeners. It performs all DB writes for an import and MUST be called
// from a single goroutine during bulk sync to avoid concurrent SQLite writes.
func (s *LibraryService) persistImported(ctx context.Context, job *importJob) error {
	dto := job.dto
	path := job.path

	// Resolve related entities. All of resolveEntities' DB writes for this
	// file land in a single SQLite transaction/fsync instead of one per
	// statement — the dominant cost for large folder scans.
	if err := s.txManager.RunInTx(ctx, func(ctx context.Context) error {
		return s.resolveEntities(ctx, dto)
	}); err != nil {
		return fmt.Errorf("failed to resolve entities for %s: %w", path, err)
	}

	// Pick up a local artist image (artist.jpg/png) sitting next to the track or
	// in the parent (artist) folder, for the track's album artists — the folder
	// belongs to the album artist's discography, not to guest/track artists.
	// During a bulk sync this is skipped — SyncFolder's batch pass handles it
	// once per directory instead of once per track.
	if s.syncing.Load() == 0 {
		var artistIDs []string
		for _, a := range dto.AlbumArtists {
			artistIDs = append(artistIDs, a.ID)
		}
		s.resolveTrackArtistImages(ctx, path, artistIDs)
	}

	// Save metadata lyrics (track ID is now resolved).
	if s.lyricsService != nil && job.lyricsSrc != "" {
		if err := s.lyricsService.SaveMetaLyrics(ctx, dto.ID, job.metaLyrics, job.lyricsSrc); err != nil {
			s.logger.Warn("Failed to save metadata lyrics", "path", path, "error", err)
		}
	}

	// Index in Search
	if err := s.searchService.IndexTrack(ctx, dto); err != nil {
		s.logger.Warn("Failed to index track", "path", path, "error", err)
	}

	// Notify internal listeners and frontend
	s.notifyTrackUpdated(dto)
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:track-updated", dto)
	}
	s.notifyAnalysisPending(dto.ID)

	return nil
}

func (s *LibraryService) resolveEntities(ctx context.Context, dto *domain.TrackDTO) error {
	// 1. Resolve Artists
	var artistIDs []string
	for _, artist := range dto.Artists {
		if id, ok := s.syncEntityCache.get(entityArtist, artist.NormalizationKey); ok {
			artist.ID = id
		} else if existing, _ := s.artistRepo.GetByNormalizationKey(ctx, artist.NormalizationKey); existing != nil {
			artist.ID = existing.ID
		} else {
			artist.ID = s.generateID(artist.NormalizationKey)
		}
		if err := s.artistRepo.Upsert(ctx, artist); err != nil {
			return err
		}
		s.syncEntityCache.set(entityArtist, artist.NormalizationKey, artist.ID)
		artistIDs = append(artistIDs, artist.ID)

		// Index artist in search
		if err := s.searchService.IndexArtist(ctx, artist); err != nil {
			s.logger.Warn("Failed to index artist", "name", artist.Name, "error", err)
		}
	}

	// 2. Resolve Album Artists
	var albumArtistIDs []string
	for _, aa := range dto.AlbumArtists {
		if id, ok := s.syncEntityCache.get(entityArtist, aa.NormalizationKey); ok {
			aa.ID = id
		} else if existing, _ := s.artistRepo.GetByNormalizationKey(ctx, aa.NormalizationKey); existing != nil {
			aa.ID = existing.ID
		} else {
			aa.ID = s.generateID(aa.NormalizationKey)
		}
		if err := s.artistRepo.Upsert(ctx, aa); err != nil {
			return err
		}
		s.syncEntityCache.set(entityArtist, aa.NormalizationKey, aa.ID)
		albumArtistIDs = append(albumArtistIDs, aa.ID)

		// Index album artist in search
		if err := s.searchService.IndexArtist(ctx, aa); err != nil {
			s.logger.Warn("Failed to index album artist", "name", aa.Name, "error", err)
		}
	}

	// 3. Resolve Album
	if dto.Album != nil && dto.Album.Title != "" {
		// Use first album artist or first artist as primary for album normalization
		primaryArtistID := ""
		if len(albumArtistIDs) > 0 {
			primaryArtistID = albumArtistIDs[0]
		} else if len(artistIDs) > 0 {
			primaryArtistID = artistIDs[0]
		}

		dto.Album.NormalizationKey = domain.NormalizationKey(dto.Album.Title) + "|" + primaryArtistID
		// Always read (rather than trusting the cache) since we need the
		// existing row's artwork key to preserve it below, not just the ID.
		existing, _ := s.albumRepo.GetByNormalizationKey(ctx, dto.Album.NormalizationKey)
		if existing != nil {
			dto.Album.ID = existing.ID
		} else if id, ok := s.syncEntityCache.get(entityAlbum, dto.Album.NormalizationKey); ok {
			dto.Album.ID = id
		} else {
			dto.Album.ID = s.generateID(dto.Album.NormalizationKey)
		}

		// Try to preserve artwork
		if dto.ArtworkKey == "" && existing != nil {
			dto.Album.ArtworkKey = existing.ArtworkKey
			dto.ArtworkKey = existing.ArtworkKey
		}

		if err := s.albumRepo.Upsert(ctx, dto.Album); err != nil {
			return err
		}
		s.syncEntityCache.set(entityAlbum, dto.Album.NormalizationKey, dto.Album.ID)

		// Use album artists if available, otherwise fall back to track artists
		finalAlbumArtistIDs := albumArtistIDs
		if len(finalAlbumArtistIDs) == 0 {
			finalAlbumArtistIDs = artistIDs
		}

		if err := s.albumRepo.SetArtists(ctx, dto.Album.ID, finalAlbumArtistIDs); err != nil {
			return err
		}
		dto.AlbumID = dto.Album.ID

		// Index album in search (need full AlbumDTO with artists populated for best indexing)
		fullAlbum := &domain.AlbumDTO{
			Album: *dto.Album,
		}
		// Populate artists from resolved album artists or track artists
		if len(albumArtistIDs) > 0 {
			fullAlbum.Artists = dto.AlbumArtists
		} else {
			fullAlbum.Artists = dto.Artists
		}

		if err := s.searchService.IndexAlbum(ctx, fullAlbum); err != nil {
			s.logger.Warn("Failed to index album", "title", fullAlbum.Title, "error", err)
		}
	}

	// 4. Resolve Genres
	var genreIDs []string
	for _, g := range dto.Genres {
		if id, ok := s.syncEntityCache.get(entityGenre, g.NormalizationKey); ok {
			g.ID = id
		} else if existing, _ := s.genreRepo.GetByNormalizationKey(ctx, g.NormalizationKey); existing != nil {
			g.ID = existing.ID
		} else {
			g.ID = s.generateID(g.NormalizationKey)
		}
		if err := s.genreRepo.Upsert(ctx, g); err != nil {
			return err
		}
		s.syncEntityCache.set(entityGenre, g.NormalizationKey, g.ID)
		genreIDs = append(genreIDs, g.ID)
	}

	// 5. Resolve Composers
	var composerIDs []string
	for _, c := range dto.Composers {
		if id, ok := s.syncEntityCache.get(entityComposer, c.NormalizationKey); ok {
			c.ID = id
		} else if existing, _ := s.composerRepo.GetByNormalizationKey(ctx, c.NormalizationKey); existing != nil {
			c.ID = existing.ID
		} else {
			c.ID = s.generateID(c.NormalizationKey)
		}
		if err := s.composerRepo.Upsert(ctx, c); err != nil {
			return err
		}
		s.syncEntityCache.set(entityComposer, c.NormalizationKey, c.ID)
		composerIDs = append(composerIDs, c.ID)

		// Index composer in search
		if err := s.searchService.IndexComposer(ctx, c); err != nil {
			s.logger.Warn("Failed to index composer", "name", c.Name, "error", err)
		}
	}

	// 6. Finalize Track
	dto.ID = s.generateID(dto.Path)

	if err := s.trackRepo.Upsert(ctx, &dto.Track); err != nil {
		return err
	}

	if err := s.trackRepo.SetArtists(ctx, dto.ID, artistIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetAlbumArtists(ctx, dto.ID, albumArtistIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetGenres(ctx, dto.ID, genreIDs); err != nil {
		return err
	}
	if err := s.trackRepo.SetComposers(ctx, dto.ID, composerIDs); err != nil {
		return err
	}

	return nil
}

// buildEntitiesFromRaw populates the split Artist/AlbumArtist/Genre/Composer
// entities on a TrackDTO from its Raw*Names fields using the configured
// per-field delimiters. Shared by ImportFile and ResplitLibrary so import and
// resync produce identical results.
//
// The multi-frame separator (domain.RawTagSeparator) is always applied first, so
// genuine multi-value tags (e.g. two ARTIST frames) always become separate
// entities regardless of the user's delimiters. Within a frame, an empty
// delimiter list means "do not split".
func (s *LibraryService) buildEntitiesFromRaw(dto *domain.TrackDTO, settings *domain.AppSettings) {
	dto.Artists = nil
	dto.AlbumArtists = nil
	dto.Genres = nil
	dto.Composers = nil

	withFrameSep := func(delimiters []string) []string {
		return append([]string{domain.RawTagSeparator}, delimiters...)
	}

	for _, name := range domain.SplitNames(dto.RawArtistNames, withFrameSep(settings.ArtistDelimiters)) {
		dto.Artists = append(dto.Artists, &domain.Artist{
			Name:             name,
			SortName:         domain.NormalizeSort(name),
			NormalizationKey: domain.NormalizationKey(name),
		})
	}
	for _, name := range domain.SplitNames(dto.RawAlbumArtistNames, withFrameSep(settings.AlbumArtistDelimiters)) {
		dto.AlbumArtists = append(dto.AlbumArtists, &domain.Artist{
			Name:             name,
			SortName:         domain.NormalizeSort(name),
			NormalizationKey: domain.NormalizationKey(name),
		})
	}
	for _, name := range domain.SplitNames(dto.RawGenreNames, withFrameSep(settings.GenreDelimiters)) {
		dto.Genres = append(dto.Genres, &domain.Genre{
			Name:             name,
			NormalizationKey: domain.NormalizationKey(name),
		})
	}
	for _, name := range domain.SplitNames(dto.RawComposerNames, withFrameSep(settings.ComposerDelimiters)) {
		dto.Composers = append(dto.Composers, &domain.Composer{
			Name:             name,
			NormalizationKey: domain.NormalizationKey(name),
		})
	}
}

// ResplitLibrary re-splits every track's stored raw tag values using the current
// delimiter settings, rebuilds the artist/album-artist/genre/composer entities
// and their junctions, then clears any entities left orphaned by the change. It
// reads only the DB (no file I/O) and reuses the existing sync progress events
// so the frontend progress dialog works unchanged.
// ResplitLibrary re-splits the whole library standalone. See syncFolderLocked's
// lock comment: do not call this from inside SyncLibrary, which already holds
// the lock — call resplitLibraryLocked directly instead.
func (s *LibraryService) ResplitLibrary(ctx context.Context) error {
	s.bulkSyncMu.Lock()
	defer s.bulkSyncMu.Unlock()
	return s.resplitLibraryLocked(ctx)
}

func (s *LibraryService) resplitLibraryLocked(ctx context.Context) error {
	s.logger.Info("Starting library re-split")

	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return fmt.Errorf("failed to load settings: %w", err)
	}

	tracks, err := s.trackRepo.GetAll(ctx)
	if err != nil {
		return fmt.Errorf("failed to load tracks: %w", err)
	}

	// Same two optimizations SyncFolder uses, missing here previously: an
	// in-run entity cache (otherwise every artist/album/genre/composer on
	// every track re-hits GetByNormalizationKey) and batched search-index
	// commits (otherwise every IndexArtist/IndexAlbum/IndexComposer call
	// below is its own fsync-ing Bleve commit) — resplitting a large library
	// touches both once per track and was correspondingly slow without them.
	s.syncEntityCache = newSyncEntityCache()
	defer func() { s.syncEntityCache = nil }()

	s.searchService.BeginSyncBatch()
	defer func() {
		if err := s.searchService.EndSyncBatch(); err != nil {
			s.logger.Warn("Failed to flush search index sync batch", "error", err)
		}
	}()

	total := len(tracks)
	emitProgress := !isBackgroundSync(ctx) // background syncs run without the dialog
	if app := application.Get(); emitProgress && app != nil && app.Event != nil {
		app.Event.Emit("library:sync-started", map[string]interface{}{
			"path":  "",
			"total": total,
		})
	}

	for i, dto := range tracks {
		s.buildEntitiesFromRaw(dto, settings)
		if err := s.txManager.RunInTx(ctx, func(ctx context.Context) error {
			return s.resolveEntities(ctx, dto)
		}); err != nil {
			s.logger.Error("Failed to re-split track", "path", dto.Path, "error", err)
		}
		if app := application.Get(); emitProgress && app != nil && app.Event != nil {
			app.Event.Emit("library:sync-progress", domain.SyncProgress{
				Current: i + 1,
				Total:   total,
				Path:    dto.Path,
			})
		}
	}

	// Drop artists/genres/composers/albums no longer referenced after re-splitting.
	s.cleanupOrphanedEntities(ctx)
	if err := s.CleanupOrphanedArtworks(ctx); err != nil {
		s.logger.Warn("Failed to cleanup orphaned artworks", "error", err)
	}

	s.logger.Info("Finished library re-split")
	if app := application.Get(); emitProgress && app != nil && app.Event != nil {
		app.Event.Emit("library:sync-finished", "")
	}
	return nil
}

// currentMetadataSchemaVersion identifies the shape of metadata extracted by
// taglibExtractor. Bump it whenever a new field is added that needs
// already-imported (unchanged-on-disk) files to be re-parsed on the next sync
// — SyncFolder ignores its unchanged-file skip when the stored version is
// older than this, forcing every file to go through Extract again once.
const currentMetadataSchemaVersion = 1

// delimitersSignature is a stable encoding of the four delimiter lists. It is
// compared against the last-applied signature to decide whether a sync needs to
// re-split the library.
func delimitersSignature(s *domain.AppSettings) string {
	b, _ := json.Marshal([][]string{
		s.ArtistDelimiters,
		s.AlbumArtistDelimiters,
		s.GenreDelimiters,
		s.ComposerDelimiters,
	})
	return string(b)
}

// DelimitersPendingResync reports whether the current delimiter settings differ
// from the ones the library data was last split with — i.e. whether the next
// Sync Library will re-split. Survives restarts (signature is persisted).
func (s *LibraryService) DelimitersPendingResync(ctx context.Context) (bool, error) {
	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return false, err
	}
	stored, err := s.syncStateRepo.GetDelimitersSignature(ctx)
	if err != nil {
		return false, err
	}
	// No baseline yet (empty signature). This happens both on a fresh install
	// and when an existing user upgrades from a version without delimiter
	// signatures. Only the latter — a non-empty library — needs to be told a
	// re-sync will re-split. A truly empty library has nothing to re-split.
	if stored == "" {
		count, err := s.trackRepo.Count(ctx)
		if err != nil {
			return false, err
		}
		return count > 0, nil
	}
	return delimitersSignature(settings) != stored, nil
}

// SyncLibrary runs a normal folder sync (new/changed/removed files) and, only
// when the delimiter settings have changed since they were last applied,
// additionally re-splits the whole library and rebuilds the search index. The
// applied-delimiters signature is persisted so the decision survives restarts.
func (s *LibraryService) SyncLibrary(ctx context.Context) error {
	// Hold the lock for this whole run (folder loop + resplit + reindex), not just
	// per sub-step — otherwise a second SyncLibrary/SyncFolder call queued on the
	// same mutex can slip in during the gap between two sub-steps and interleave
	// with this one (e.g. its ResplitLibrary running between our ResplitLibrary
	// and our ReindexAll), redoing the same work instead of being skipped/merged.
	s.bulkSyncMu.Lock()
	defer s.bulkSyncMu.Unlock()

	folders, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		return fmt.Errorf("failed to load watched folders: %w", err)
	}
	for _, folder := range folders {
		_ = s.syncFolderLocked(ctx, folder.Path)
	}

	if schemaVersion, err := s.syncStateRepo.GetMetadataSchemaVersion(ctx); err != nil {
		s.logger.Warn("Failed to load metadata schema version", "error", err)
	} else if schemaVersion < currentMetadataSchemaVersion {
		if err := s.syncStateRepo.SetMetadataSchemaVersion(ctx, currentMetadataSchemaVersion); err != nil {
			s.logger.Warn("Failed to persist metadata schema version", "error", err)
		}
	}

	settings, err := s.settingsRepo.Load(ctx)
	if err != nil {
		return fmt.Errorf("failed to load settings: %w", err)
	}
	sig := delimitersSignature(settings)
	stored, err := s.syncStateRepo.GetDelimitersSignature(ctx)
	if err != nil {
		s.logger.Warn("Failed to load delimiters signature", "error", err)
		stored = ""
	}
	if sig == stored {
		return nil
	}

	s.logger.Info("Delimiters changed since last sync; re-splitting library")
	if err := s.resplitLibraryLocked(ctx); err != nil {
		return err
	}
	// Persist the signature before the search reindex so the final
	// library:sync-finished event (emitted from ReindexAll) already reflects the
	// applied state — otherwise the UI's post-sync recheck still sees a mismatch.
	if err := s.syncStateRepo.SetDelimitersSignature(ctx, sig); err != nil {
		s.logger.Warn("Failed to persist delimiters signature", "error", err)
	}
	if err := s.reindexAllLocked(ctx); err != nil {
		s.logger.Warn("Re-index after re-split failed", "error", err)
	}
	return nil
}

func (s *LibraryService) generateID(seed string) string {
	return uuid.NewMD5(uuid.NameSpaceURL, []byte(seed)).String()
}

func isSubPath(parent, child string) bool {
	rel, err := filepath.Rel(parent, child)
	if err != nil {
		return false
	}
	return !strings.HasPrefix(rel, "..") && rel != ".." && rel != "."
}

// IsPathValid returns nil if path exists on disk, has a supported extension, and
// lives under one of the app's watched folders.
func (s *LibraryService) IsPathValid(ctx context.Context, path string) error {
	if _, err := os.Stat(path); err != nil {
		return fmt.Errorf("file not found: %w", err)
	}
	ext := strings.ToLower(filepath.Ext(path))
	if !SupportedAudioExtensions[ext] {
		return fmt.Errorf("unsupported format: %s", ext)
	}
	folders, err := s.watchedFolderRepo.GetAll(ctx)
	if err != nil {
		return err
	}
	for _, f := range folders {
		if isSubPath(f.Path, path) {
			return nil
		}
	}
	return fmt.Errorf("path not under any watched folder")
}

// EnsureTrack returns the TrackDTO for the given path, importing it from disk
// if it is not yet in the library. For newly imported tracks, fallbackTitle and
// fallbackArtist are applied only when the file's own tags are empty.
func (s *LibraryService) EnsureTrack(ctx context.Context, path, fallbackTitle, fallbackArtist string) (*domain.TrackDTO, error) {
	track, err := s.trackRepo.GetByPath(ctx, path)
	if err != nil {
		return nil, err
	}
	if track != nil {
		return track, nil
	}

	if err := s.ImportFile(ctx, path); err != nil {
		return nil, err
	}
	track, err = s.trackRepo.GetByPath(ctx, path)
	if err != nil || track == nil {
		return nil, fmt.Errorf("track missing after import: %s", path)
	}

	changed := false
	if track.Title == "" && fallbackTitle != "" {
		track.Title = fallbackTitle
		changed = true
	}
	if track.RawArtistNames == "" && fallbackArtist != "" {
		track.RawArtistNames = fallbackArtist
		changed = true
	}
	if changed {
		_ = s.trackRepo.Upsert(ctx, &track.Track)
	}
	return track, nil
}

// ShowInExplorer opens the native file explorer and selects the file.
func (s *LibraryService) ShowInExplorer(ctx context.Context, id string) error {
	track, err := s.trackRepo.GetByID(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to get track: %w", err)
	}
	if track == nil {
		return fmt.Errorf("track not found: %s", id)
	}

	path := track.Path
	var cmd *exec.Cmd

	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", "-R", path)
	case "windows":
		cmd = exec.Command("explorer.exe", "/select,", path)
	default: // linux and others
		cmd = exec.Command("xdg-open", filepath.Dir(path))
	}

	return cmd.Run()
}

// ToggleFavorite toggles the favorite state of a track. Returns the new state.
func (s *LibraryService) ToggleFavorite(ctx context.Context, id string) (bool, error) {
	newState, err := s.trackRepo.ToggleFavorite(ctx, id)
	if err != nil {
		return false, fmt.Errorf("failed to toggle favorite: %w", err)
	}
	dto, err := s.trackRepo.GetByID(ctx, id)
	if err == nil && dto != nil {
		s.notifyTrackUpdated(dto)
		s.notifyFavoriteChanged(dto)
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("library:track-updated", dto)
		}
	}
	return newState, nil
}

// UpdateMetadata writes tag changes to the audio file and re-imports to update DB and search.
func (s *LibraryService) UpdateMetadata(ctx context.Context, id string, fields domain.MetadataUpdate) error {
	track, err := s.trackRepo.GetByID(ctx, id)
	if err != nil {
		return fmt.Errorf("failed to get track: %w", err)
	}
	if track == nil {
		return fmt.Errorf("track not found: %s", id)
	}
	if err := s.metadataWriter.WriteMetadata(ctx, track.Path, fields); err != nil {
		return fmt.Errorf("failed to write metadata: %w", err)
	}
	if err := s.ImportFile(ctx, track.Path); err != nil {
		return err
	}

	// A metadata edit can move the track to a different album/artist/genre, leaving
	// the originals with zero tracks. Prune those orphans (DB + search index) so
	// they don't linger, then tell the frontend to reload list views.
	s.cleanupOrphanedEntities(ctx)
	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("library:updated", nil)
	}
	return nil
}

// GetAlbumColors returns the theme colors for an album's artwork.
func (s *LibraryService) GetAlbumColors(ctx context.Context, id string) (*domain.ThemeColors, error) {
	album, err := s.albumRepo.GetByID(ctx, id)
	if err != nil {
		return nil, fmt.Errorf("failed to get album: %w", err)
	}
	if album == nil {
		return nil, fmt.Errorf("album not found: %s", id)
	}

	if album.ArtworkKey == "" {
		return nil, nil
	}

	path := s.artworkCache.GetPath(album.ArtworkKey)
	colors, err := artwork.ExtractPalette(path)
	if err != nil {
		return nil, fmt.Errorf("failed to extract palette: %w", err)
	}

	return colors, nil
}

// ReindexAll rebuilds the search index standalone. See syncFolderLocked's lock
// comment: do not call this from inside SyncLibrary, which already holds the
// lock — call reindexAllLocked directly instead.
func (s *LibraryService) ReindexAll(ctx context.Context) error {
	s.bulkSyncMu.Lock()
	defer s.bulkSyncMu.Unlock()
	return s.reindexAllLocked(ctx)
}

func (s *LibraryService) reindexAllLocked(ctx context.Context) error {
	s.logger.Info("Starting full library re-indexing")

	// Reset search index to apply the new mapping/analyzer
	if err := s.searchService.Reset(ctx); err != nil {
		s.logger.Error("Failed to reset search index", "error", err)
		return fmt.Errorf("failed to reset search index: %w", err)
	}

	// Calculate total items
	tracks, _ := s.trackRepo.GetAll(ctx)
	albums, _ := s.albumRepo.GetAll(ctx)
	artists, _ := s.artistRepo.GetAll(ctx)
	composers, _ := s.composerRepo.GetAll(ctx)
	playlists, _ := s.playlistRepo.GetAll(ctx)

	total := len(tracks) + len(albums) + len(artists) + len(composers) + len(playlists)
	current := 0

	showDialog := !isBackgroundSync(ctx) // background syncs run without the dialog
	emitProgress := func(path string) {
		current++
		if app := application.Get(); showDialog && app != nil && app.Event != nil {
			app.Event.Emit("library:sync-progress", domain.SyncProgress{
				Current: current,
				Total:   total,
				Path:    path,
			})
		}
	}

	if app := application.Get(); showDialog && app != nil && app.Event != nil {
		app.Event.Emit("library:sync-started", map[string]interface{}{
			"path":  "Re-indexing Search",
			"total": total,
		})
	}

	// Resolve full album DTOs (the GetAll result lacks the relations the
	// index needs).
	albumDTOs := make([]*domain.AlbumDTO, 0, len(albums))
	for _, a := range albums {
		if dto, _ := s.albumRepo.GetByID(ctx, a.ID); dto != nil {
			albumDTOs = append(albumDTOs, dto)
		}
	}

	// Batch the writes: one fsync per chunk instead of one per document.
	// Per-document commits make a full reindex appear to hang on Windows
	// (slow NTFS fsync) for large libraries.
	err := s.searchService.BatchReindex(ctx, &domain.ReindexData{
		Tracks:    tracks,
		Albums:    albumDTOs,
		Artists:   artists,
		Composers: composers,
		Playlists: playlists,
	}, emitProgress)
	if err != nil {
		s.logger.Warn("Re-indexing did not complete", "error", err)
	}

	s.logger.Info("Finished full library re-indexing")
	if app := application.Get(); showDialog && app != nil && app.Event != nil {
		app.Event.Emit("library:sync-finished", "Search Index")
	}
	return err
}
