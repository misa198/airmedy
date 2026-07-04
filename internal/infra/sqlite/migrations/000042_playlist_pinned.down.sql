DROP INDEX IF EXISTS idx_playlists_pinned_at;
ALTER TABLE playlists DROP COLUMN pinned_at;
