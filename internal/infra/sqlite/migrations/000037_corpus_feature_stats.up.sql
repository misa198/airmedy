CREATE TABLE IF NOT EXISTS feature_percentiles (
    feature_name TEXT PRIMARY KEY,
    p1 REAL NOT NULL,
    p5 REAL NOT NULL,
    p50 REAL NOT NULL,
    p95 REAL NOT NULL,
    p99 REAL NOT NULL,
    sample_count INTEGER NOT NULL,
    computed_at DATETIME NOT NULL
);

ALTER TABLE app_settings ADD COLUMN mood_derivation_version INTEGER NOT NULL DEFAULT 0;
