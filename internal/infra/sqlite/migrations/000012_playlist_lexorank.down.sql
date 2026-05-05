-- Revert to INTEGER position
CREATE TABLE playlist_tracks_old (
    playlist_id TEXT NOT NULL,
    track_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (playlist_id, track_id),
    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

-- Copy data and convert string position back to integer using row number
INSERT INTO playlist_tracks_old (playlist_id, track_id, position)
SELECT 
    playlist_id, 
    track_id, 
    (ROW_NUMBER() OVER (PARTITION BY playlist_id ORDER BY position)) - 1
FROM playlist_tracks;

DROP TABLE playlist_tracks;
ALTER TABLE playlist_tracks_old RENAME TO playlist_tracks;
