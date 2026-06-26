ALTER TABLE app_settings ADD COLUMN lyrics_folder_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_settings ADD COLUMN lyrics_folder_path TEXT NOT NULL DEFAULT '';
