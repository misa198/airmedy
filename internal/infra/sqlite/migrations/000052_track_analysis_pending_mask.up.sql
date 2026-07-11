-- Materialized pending state keeps progress and backfill queries proportional
-- to unresolved work instead of scanning every track for component versions.
ALTER TABLE tracks ADD COLUMN analysis_pending_mask INTEGER NOT NULL DEFAULT 3;

-- 1 = FFmpeg, 2 = aubio. A failed component at the current version is
-- resolved (and therefore has no pending bit), matching the worker policy.
UPDATE tracks
SET analysis_pending_mask =
    (CASE WHEN NOT EXISTS (
        SELECT 1 FROM track_analysis_components c
        WHERE c.track_id = tracks.id AND c.component = 'ffmpeg' AND c.version >= 1
    ) THEN 1 ELSE 0 END)
    |
    (CASE WHEN NOT EXISTS (
        SELECT 1 FROM track_analysis_components c
        WHERE c.track_id = tracks.id AND c.component = 'aubio' AND c.version >= 1
    ) THEN 2 ELSE 0 END);

CREATE INDEX IF NOT EXISTS idx_tracks_analysis_pending_mask
    ON tracks(analysis_pending_mask)
    WHERE analysis_pending_mask != 0;
