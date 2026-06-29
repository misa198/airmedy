-- Update default delimiter from "\" to "\\" for users still on the old default.
UPDATE app_settings SET artist_delimiters = '[";","\\\\",","]' WHERE artist_delimiters = '[";","\\",","]';
UPDATE app_settings SET album_artist_delimiters = '[";","\\\\",","]' WHERE album_artist_delimiters = '[";","\\",","]';
UPDATE app_settings SET genre_delimiters = '[";","\\\\",","]' WHERE genre_delimiters = '[";","\\",","]';
UPDATE app_settings SET composer_delimiters = '[";","\\\\",","]' WHERE composer_delimiters = '[";","\\",","]';
