# Memory Reduction Plan

## Root causes
- Go (700MB): Bleve stores all fields in memory; Go GC holds OS pages
- WebView (900MB): entire track library loaded into JS/Vue reactive system on every view mount; HomeView calls GetAllTracks() just to check length > 0

## Changes (in order)

### 1. Add Count() to TrackRepository → fix HomeView [LOW RISK]
Files:
- `internal/domain/repositories.go` — add `Count(ctx) (int, error)` to TrackRepository interface
- `internal/infra/sqlite/track_repository.go` — implement Count()
- `internal/infra/wails/library_service.go` — add GetTrackCount()
- `frontend/src/views/HomeView.vue` — replace GetAllTracks() with GetTrackCount()

### 2. Paginate TracksView [HIGH IMPACT]
TracksView loads all tracks eagerly. Replace with paginated fetch + virtual scroll
that loads pages on demand.

Files:
- `internal/domain/repositories.go` — add `GetPaginated(ctx, offset, limit int) ([]*TrackDTO, error)`
- `internal/infra/sqlite/track_repository.go` — implement GetPaginated()
- `internal/infra/wails/library_service.go` — add GetTracksPaginated(offset, limit)
- `frontend/src/views/TracksView.vue` — fetch total count + paginate via scroll

### 3. Bleve: Store: false on text fields [LOW RISK, ~50% index memory]
Search results only need id/type (keyword fields). Text fields don't need to be
stored — only indexed.

Files:
- `internal/infra/bleve/bleve.go` — set textFieldMapping.Store = false

### 4. Go GC tuning [LOW RISK]
Files:
- `main.go` — add runtime/debug import, call debug.SetGCPercent(50) at startup

## Out of scope (too risky now)
- Queue stores full TrackDTOs (originalList + shuffledList) — refactoring to ID-only
  queue requires large changes to PlayerService/QueueService and all callers.
