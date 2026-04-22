-- Add normalization_key to entities
ALTER TABLE artists ADD COLUMN normalization_key TEXT;
ALTER TABLE albums ADD COLUMN normalization_key TEXT;
ALTER TABLE genres ADD COLUMN normalization_key TEXT;
ALTER TABLE composers ADD COLUMN normalization_key TEXT;

-- Add raw name columns to tracks
ALTER TABLE tracks ADD COLUMN raw_artist_names TEXT;
ALTER TABLE tracks ADD COLUMN raw_album_artist_names TEXT;
ALTER TABLE tracks ADD COLUMN raw_genre_names TEXT;
ALTER TABLE tracks ADD COLUMN raw_composer_names TEXT;

-- Create junction tables for Many-to-Many relationships
CREATE TABLE IF NOT EXISTS track_artists (
    track_id TEXT NOT NULL,
    artist_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (track_id, artist_id),
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS track_album_artists (
    track_id TEXT NOT NULL,
    artist_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (track_id, artist_id),
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS track_genres (
    track_id TEXT NOT NULL,
    genre_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (track_id, genre_id),
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS track_composers (
    track_id TEXT NOT NULL,
    composer_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (track_id, composer_id),
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    FOREIGN KEY (composer_id) REFERENCES composers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS album_artists (
    album_id TEXT NOT NULL,
    artist_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (album_id, artist_id),
    FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE,
    FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_artists_normalization_key ON artists(normalization_key);
CREATE INDEX IF NOT EXISTS idx_albums_normalization_key ON albums(normalization_key);
CREATE INDEX IF NOT EXISTS idx_genres_normalization_key ON genres(normalization_key);
CREATE INDEX IF NOT EXISTS idx_composers_normalization_key ON composers(normalization_key);

-- Optional: Migrate existing data (this is tricky because we need to split strings)
-- For now, we'll leave it to the next sync to populate these.
-- But we can at least fill normalization_key for existing records if we want.
-- However, since this is an alpha/dev project, we might just re-sync.
