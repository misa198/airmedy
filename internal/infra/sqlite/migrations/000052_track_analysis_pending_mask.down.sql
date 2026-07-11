DROP INDEX IF EXISTS idx_tracks_analysis_pending_mask;
ALTER TABLE tracks DROP COLUMN analysis_pending_mask;
