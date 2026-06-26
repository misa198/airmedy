ALTER TABLE app_settings ADD COLUMN lyrics_folder_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_settings ADD COLUMN lyrics_folder_path TEXT NOT NULL DEFAULT '';
ALTER TABLE app_settings ADD COLUMN lyrics_subfolder_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_settings ADD COLUMN lyrics_subfolder_name TEXT NOT NULL DEFAULT '';
