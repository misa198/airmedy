ALTER TABLE app_settings ADD COLUMN tray_living_artwork_background BOOLEAN NOT NULL DEFAULT 0;
UPDATE app_settings SET tray_living_artwork_background = (tray_background_mode = 'living');
ALTER TABLE app_settings DROP COLUMN tray_background_mode;
