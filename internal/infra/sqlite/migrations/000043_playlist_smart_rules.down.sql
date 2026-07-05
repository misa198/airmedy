DROP INDEX IF EXISTS idx_playlists_is_smart;
ALTER TABLE playlists DROP COLUMN rules;
ALTER TABLE playlists DROP COLUMN is_smart;
