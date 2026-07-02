DROP TABLE IF EXISTS track_features;
-- tracks.analyzed_version and app_settings.normalization_* columns are not dropped:
-- SQLite DROP COLUMN is unsafe across versions; treated as irreversible in development.
