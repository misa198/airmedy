CREATE TABLE mobile_sync_lyric_cache (
    track_id TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
    content TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT '',
    has_lyric BOOLEAN NOT NULL DEFAULT FALSE,
    version TEXT NOT NULL DEFAULT '',
    fingerprint TEXT NOT NULL DEFAULT '',
    updated_at DATETIME NOT NULL
);
