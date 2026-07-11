-- Corpus-normalized spectral centroid for direct Mood Radio similarity.
-- This is a derived score; raw spectral_centroid remains the source value.
ALTER TABLE track_features ADD COLUMN brightness REAL;

-- Force startup's stale-percentile path to recompute and backfill every
-- existing mood row with the new derived score before Radio requires it.
UPDATE feature_percentiles SET computed_at = '1970-01-01T00:00:00Z';
UPDATE tracks SET mood_derived_version = 0;
