CREATE TABLE IF NOT EXISTS eq_profiles (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS eq_bands (
    profile_id TEXT NOT NULL,
    band_index INTEGER NOT NULL,
    frequency REAL NOT NULL,
    gain REAL NOT NULL DEFAULT 0.0,
    bandwidth REAL NOT NULL DEFAULT 1.0,
    PRIMARY KEY (profile_id, band_index),
    FOREIGN KEY (profile_id) REFERENCES eq_profiles(id) ON DELETE CASCADE
);
