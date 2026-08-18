ALTER TABLE listening_sessions ADD COLUMN source_device_id TEXT NOT NULL DEFAULT 'desktop';
ALTER TABLE playback_attempts ADD COLUMN source_device_id TEXT NOT NULL DEFAULT 'desktop';
UPDATE listening_sessions SET source_device_id = COALESCE((SELECT device_id FROM pairing_identity WHERE id = 1), 'desktop');
UPDATE playback_attempts SET source_device_id = COALESCE((SELECT device_id FROM pairing_identity WHERE id = 1), 'desktop');

ALTER TABLE daily_track_listening_stats RENAME TO daily_track_listening_stats_old;
CREATE TABLE daily_track_listening_stats (
    source_device_id TEXT NOT NULL DEFAULT 'desktop',
    local_date TEXT NOT NULL,
    track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    listened_seconds INTEGER NOT NULL DEFAULT 0,
    play_count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (source_device_id, local_date, track_id)
);
INSERT INTO daily_track_listening_stats SELECT COALESCE((SELECT device_id FROM pairing_identity WHERE id = 1), 'desktop'), local_date, track_id, listened_seconds, play_count FROM daily_track_listening_stats_old;
DROP TABLE daily_track_listening_stats_old;
CREATE INDEX idx_daily_track_listening_stats_date ON daily_track_listening_stats(local_date);

ALTER TABLE daily_playback_attempt_stats RENAME TO daily_playback_attempt_stats_old;
CREATE TABLE daily_playback_attempt_stats (
    source_device_id TEXT NOT NULL DEFAULT 'desktop',
    local_date TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    completed INTEGER NOT NULL DEFAULT 0,
    skipped INTEGER NOT NULL DEFAULT 0,
    stopped INTEGER NOT NULL DEFAULT 0,
    listened_seconds INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (source_device_id, local_date)
);
INSERT INTO daily_playback_attempt_stats SELECT COALESCE((SELECT device_id FROM pairing_identity WHERE id = 1), 'desktop'), local_date, attempts, completed, skipped, stopped, listened_seconds FROM daily_playback_attempt_stats_old;
DROP TABLE daily_playback_attempt_stats_old;
