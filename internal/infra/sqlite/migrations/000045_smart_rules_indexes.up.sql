-- Single-column indexes for the smart-playlist rule fields most likely to
-- run a range predicate (gt/lt/gte/lte/between) with LiveUpdating=true,
-- i.e. recomputed on every playlist read: mood queries (energy/danceability)
-- are the hottest path since Mood tab playlists default to live-updating.
CREATE INDEX IF NOT EXISTS idx_tracks_year ON tracks(year);
CREATE INDEX IF NOT EXISTS idx_tracks_bpm ON tracks(bpm);
CREATE INDEX IF NOT EXISTS idx_tracks_duration ON tracks(duration);
CREATE INDEX IF NOT EXISTS idx_tracks_bitrate ON tracks(bitrate);
CREATE INDEX IF NOT EXISTS idx_tracks_play_count ON tracks(play_count);
CREATE INDEX IF NOT EXISTS idx_tracks_created_at ON tracks(created_at);
CREATE INDEX IF NOT EXISTS idx_track_features_energy ON track_features(energy);
CREATE INDEX IF NOT EXISTS idx_track_features_danceability ON track_features(danceability);
