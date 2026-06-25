ALTER TABLE app_settings ADD COLUMN prefer_local_artist_artwork BOOLEAN NOT NULL DEFAULT 1;
ALTER TABLE app_settings ADD COLUMN last_scan_version TEXT NOT NULL DEFAULT '';
