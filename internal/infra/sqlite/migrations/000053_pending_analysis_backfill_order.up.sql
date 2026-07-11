-- Serve bounded analysis backfill in stable oldest-added order without a
-- temp sort over all unresolved tracks.
CREATE INDEX IF NOT EXISTS idx_tracks_analysis_pending_backfill
    ON tracks(created_at, id)
    WHERE analysis_pending_mask != 0;
