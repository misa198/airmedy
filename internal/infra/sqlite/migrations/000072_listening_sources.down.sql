ALTER TABLE listening_sessions DROP COLUMN source_device_id;
ALTER TABLE playback_attempts DROP COLUMN source_device_id;

ALTER TABLE daily_track_listening_stats RENAME TO daily_track_listening_stats_old;
CREATE TABLE daily_track_listening_stats (local_date TEXT NOT NULL, track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE, listened_seconds INTEGER NOT NULL DEFAULT 0, play_count INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (local_date, track_id));
INSERT INTO daily_track_listening_stats SELECT local_date, track_id, SUM(listened_seconds), SUM(play_count) FROM daily_track_listening_stats_old GROUP BY local_date, track_id;
DROP TABLE daily_track_listening_stats_old;
CREATE INDEX idx_daily_track_listening_stats_date ON daily_track_listening_stats(local_date);

ALTER TABLE daily_playback_attempt_stats RENAME TO daily_playback_attempt_stats_old;
CREATE TABLE daily_playback_attempt_stats (local_date TEXT PRIMARY KEY, attempts INTEGER NOT NULL DEFAULT 0, completed INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0, stopped INTEGER NOT NULL DEFAULT 0, listened_seconds INTEGER NOT NULL DEFAULT 0);
INSERT INTO daily_playback_attempt_stats SELECT local_date, SUM(attempts), SUM(completed), SUM(skipped), SUM(stopped), SUM(listened_seconds) FROM daily_playback_attempt_stats_old GROUP BY local_date;
DROP TABLE daily_playback_attempt_stats_old;
