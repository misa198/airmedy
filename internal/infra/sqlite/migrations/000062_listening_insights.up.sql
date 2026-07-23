CREATE TABLE IF NOT EXISTS listening_sessions (
    id TEXT PRIMARY KEY,
    track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NOT NULL,
    listened_seconds INTEGER NOT NULL,
    qualified_play INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_listening_sessions_started_at ON listening_sessions(started_at);
CREATE INDEX IF NOT EXISTS idx_listening_sessions_ended_at ON listening_sessions(ended_at);

CREATE TABLE IF NOT EXISTS daily_track_listening_stats (
    local_date TEXT NOT NULL,
    track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    listened_seconds INTEGER NOT NULL DEFAULT 0,
    play_count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (local_date, track_id)
);

CREATE INDEX IF NOT EXISTS idx_daily_track_listening_stats_date ON daily_track_listening_stats(local_date);
