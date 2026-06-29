CREATE TABLE IF NOT EXISTS library_sync_state (
    id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
    delimiters_signature TEXT NOT NULL DEFAULT '',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (id = 1)
);
INSERT OR IGNORE INTO library_sync_state (id) VALUES (1);
