ALTER TABLE app_settings ADD COLUMN tray_background_mode TEXT NOT NULL DEFAULT 'solid';
UPDATE app_settings SET tray_background_mode = CASE WHEN tray_living_artwork_background THEN 'living' ELSE 'solid' END;
ALTER TABLE app_settings DROP COLUMN tray_living_artwork_background;
