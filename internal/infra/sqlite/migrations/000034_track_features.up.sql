CREATE TABLE IF NOT EXISTS track_features (
    track_id TEXT PRIMARY KEY,
    analyzer_version INTEGER NOT NULL DEFAULT 0,
    analyzed_at DATETIME,
    -- loudness / dynamics (ebur128 + astats)
    loudness_lufs REAL,
    loudness_range REAL,
    true_peak REAL,
    rms REAL,
    crest REAL,
    -- spectral (aspectralstats)
    spectral_centroid REAL,
    spectral_rolloff REAL,
    spectral_flatness REAL,
    spectral_flux REAL,
    zcr REAL,
    -- reserved-null mood columns
    tempo REAL,
    musical_key TEXT,
    mode TEXT,
    valence REAL,
    energy REAL,
    danceability REAL,
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

ALTER TABLE tracks ADD COLUMN analyzed_version INTEGER NOT NULL DEFAULT 0; -- 0 = pending
CREATE INDEX IF NOT EXISTS idx_tracks_analyzed_version ON tracks(analyzed_version);

ALTER TABLE app_settings ADD COLUMN normalization_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_settings ADD COLUMN normalization_mode TEXT NOT NULL DEFAULT 'track';    -- off|track|album
ALTER TABLE app_settings ADD COLUMN normalization_target_lufs REAL NOT NULL DEFAULT -14;
ALTER TABLE app_settings ADD COLUMN normalization_prevent_clip INTEGER NOT NULL DEFAULT 1;
ALTER TABLE app_settings ADD COLUMN library_analysis_enabled INTEGER NOT NULL DEFAULT 0;
