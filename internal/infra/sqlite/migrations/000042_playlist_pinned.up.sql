ALTER TABLE playlists ADD COLUMN pinned_at DATETIME NULL;
CREATE INDEX IF NOT EXISTS idx_playlists_pinned_at ON playlists(pinned_at);
