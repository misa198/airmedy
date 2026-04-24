CREATE TABLE IF NOT EXISTS player_state (
    id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
    queue_track_ids TEXT NOT NULL DEFAULT '[]',
    current_track_id TEXT,
    position REAL NOT NULL DEFAULT 0,
    volume REAL NOT NULL DEFAULT 1.0,
    muted INTEGER NOT NULL DEFAULT 0,
    shuffle INTEGER NOT NULL DEFAULT 0,
    repeat_mode TEXT NOT NULL DEFAULT 'off',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (id = 1)
);
INSERT OR IGNORE INTO player_state (id) VALUES (1);
