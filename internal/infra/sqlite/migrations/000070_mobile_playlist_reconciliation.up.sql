CREATE TABLE mobile_playlist_mutation_lww (
    playlist_id TEXT PRIMARY KEY,
    updated_at INTEGER NOT NULL,
    mutation_id TEXT NOT NULL,
    deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE mobile_playlist_artwork_staging (
    reconciliation_id TEXT NOT NULL,
    device_id TEXT NOT NULL REFERENCES paired_mobile_devices(device_id) ON DELETE CASCADE,
    sha256 TEXT NOT NULL,
    artwork_key TEXT NOT NULL,
    expires_at DATETIME NOT NULL,
    PRIMARY KEY (reconciliation_id, device_id, sha256)
);
CREATE INDEX idx_mobile_playlist_artwork_staging_expiry ON mobile_playlist_artwork_staging(expires_at);
