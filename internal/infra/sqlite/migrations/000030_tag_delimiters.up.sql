ALTER TABLE app_settings ADD COLUMN artist_delimiters TEXT NOT NULL DEFAULT '[";","\\",","]';
ALTER TABLE app_settings ADD COLUMN album_artist_delimiters TEXT NOT NULL DEFAULT '[";","\\",","]';
ALTER TABLE app_settings ADD COLUMN genre_delimiters TEXT NOT NULL DEFAULT '[";","\\",","]';
ALTER TABLE app_settings ADD COLUMN composer_delimiters TEXT NOT NULL DEFAULT '[";","\\",","]';
