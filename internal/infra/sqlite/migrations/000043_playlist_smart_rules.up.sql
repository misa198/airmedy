ALTER TABLE playlists ADD COLUMN is_smart INTEGER NOT NULL DEFAULT 0;
ALTER TABLE playlists ADD COLUMN rules TEXT;
CREATE INDEX IF NOT EXISTS idx_playlists_is_smart ON playlists(is_smart);
