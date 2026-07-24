CREATE TABLE IF NOT EXISTS playback_attempts (
    id TEXT PRIMARY KEY,
    track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    started_at DATETIME NOT NULL,
    ended_at DATETIME,
    start_position_seconds REAL NOT NULL DEFAULT 0,
    listened_seconds INTEGER NOT NULL DEFAULT 0,
    end_reason TEXT CHECK (end_reason IN ('completed', 'skipped', 'stopped'))
);

CREATE INDEX IF NOT EXISTS idx_playback_attempts_started_at ON playback_attempts(started_at);
CREATE INDEX IF NOT EXISTS idx_playback_attempts_ended_at ON playback_attempts(ended_at);

CREATE TABLE IF NOT EXISTS daily_playback_attempt_stats (
    local_date TEXT PRIMARY KEY,
    attempts INTEGER NOT NULL DEFAULT 0,
    completed INTEGER NOT NULL DEFAULT 0,
    skipped INTEGER NOT NULL DEFAULT 0,
    stopped INTEGER NOT NULL DEFAULT 0
);
