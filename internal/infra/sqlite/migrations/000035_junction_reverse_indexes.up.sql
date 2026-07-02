-- Reverse-lookup columns on junction tables had no index — only the
-- composite PRIMARY KEY (track_id, artist_id/genre_id/composer_id), which is
-- useless for queries filtering by the second column alone. That made
-- DeleteOrphaned's "id NOT IN (SELECT artist_id FROM track_artists)" style
-- anti-join queries (run after every deletion, e.g. RemoveWatchedFolder) do a
-- full scan of the junction table per candidate row instead of an index
-- lookup — tens of seconds on a library of any real size. Also speeds up
-- GetByArtistID/GetByGenreID/GetByComposerID/GetAlbumsByArtistID.
CREATE INDEX IF NOT EXISTS idx_track_artists_artist_id ON track_artists(artist_id);
CREATE INDEX IF NOT EXISTS idx_track_album_artists_artist_id ON track_album_artists(artist_id);
CREATE INDEX IF NOT EXISTS idx_track_genres_genre_id ON track_genres(genre_id);
CREATE INDEX IF NOT EXISTS idx_track_composers_composer_id ON track_composers(composer_id);
CREATE INDEX IF NOT EXISTS idx_album_artists_artist_id ON album_artists(artist_id);
