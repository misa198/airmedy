package library

// entityKind identifies which cache map syncEntityCache.get/set operates on.
type entityKind int

const (
	entityArtist entityKind = iota
	entityAlbum
	entityGenre
	entityComposer
)

// syncEntityCache caches normalization-key -> resolved-ID lookups for the
// duration of one SyncFolder run, so repeated entities (e.g. one artist
// across hundreds of tracks) don't re-hit GetByNormalizationKey for every
// file. Only the sync consumer goroutine touches it, so no locking is
// needed. A nil *syncEntityCache (outside a sync run) makes get/set safe
// no-ops, falling back to the always-hit-DB behavior.
type syncEntityCache struct {
	artists   map[string]string
	albums    map[string]string
	genres    map[string]string
	composers map[string]string
}

func newSyncEntityCache() *syncEntityCache {
	return &syncEntityCache{
		artists:   make(map[string]string),
		albums:    make(map[string]string),
		genres:    make(map[string]string),
		composers: make(map[string]string),
	}
}

func (c *syncEntityCache) mapFor(kind entityKind) map[string]string {
	switch kind {
	case entityArtist:
		return c.artists
	case entityAlbum:
		return c.albums
	case entityGenre:
		return c.genres
	case entityComposer:
		return c.composers
	default:
		return nil
	}
}

func (c *syncEntityCache) get(kind entityKind, key string) (string, bool) {
	if c == nil {
		return "", false
	}
	id, ok := c.mapFor(kind)[key]
	return id, ok
}

func (c *syncEntityCache) set(kind entityKind, key, id string) {
	if c == nil {
		return
	}
	c.mapFor(kind)[key] = id
}
