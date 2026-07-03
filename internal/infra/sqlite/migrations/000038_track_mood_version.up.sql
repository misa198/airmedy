ALTER TABLE tracks ADD COLUMN mood_derived_version INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_tracks_mood_derived_version ON tracks(mood_derived_version);
