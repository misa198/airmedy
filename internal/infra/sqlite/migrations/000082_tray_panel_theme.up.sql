ALTER TABLE app_settings ADD COLUMN tray_panel_theme TEXT NOT NULL DEFAULT 'dark';
UPDATE app_settings SET tray_panel_theme = 'light' WHERE tray_theme = 'light';
