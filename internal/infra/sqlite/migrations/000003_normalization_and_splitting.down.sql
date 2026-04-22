-- Drop indexes
DROP INDEX IF EXISTS idx_artists_normalization_key;
DROP INDEX IF EXISTS idx_albums_normalization_key;
DROP INDEX IF EXISTS idx_genres_normalization_key;
DROP INDEX IF EXISTS idx_composers_normalization_key;

-- Drop junction tables
DROP TABLE IF EXISTS album_artists;
DROP TABLE IF EXISTS track_composers;
DROP TABLE IF EXISTS track_genres;
DROP TABLE IF EXISTS track_album_artists;
DROP TABLE IF EXISTS track_artists;

-- Remove columns from tracks
-- SQLite doesn't support DROP COLUMN in older versions, but recent ones do.
-- For safety, we can just leave them or do the table recreation dance.
-- Since this is a migration script, we'll try DROP COLUMN if supported.
ALTER TABLE tracks DROP COLUMN raw_artist_names;
ALTER TABLE tracks DROP COLUMN raw_album_artist_names;
ALTER TABLE tracks DROP COLUMN raw_genre_names;
ALTER TABLE tracks DROP COLUMN raw_composer_names;

-- Remove normalization_key from entities
ALTER TABLE artists DROP COLUMN normalization_key;
ALTER TABLE albums DROP COLUMN normalization_key;
ALTER TABLE genres DROP COLUMN normalization_key;
ALTER TABLE composers DROP COLUMN normalization_key;
