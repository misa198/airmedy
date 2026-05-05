-- Create new table with TEXT position
CREATE TABLE playlist_tracks_new (
    playlist_id TEXT NOT NULL,
    track_id TEXT NOT NULL,
    position TEXT NOT NULL,
    PRIMARY KEY (playlist_id, track_id),
    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

-- Recursive CTE to populate LexoRank strings
WITH RECURSIVE
  -- Step 1: Number all existing tracks per playlist
  OrderedTracks AS (
    SELECT 
      playlist_id, 
      track_id, 
      ROW_NUMBER() OVER (PARTITION BY playlist_id ORDER BY position) as rn
    FROM playlist_tracks
  ),
  -- Step 2: Recursively calculate the next LexoRank
  GeneratedRanks(playlist_id, track_id, rn, rank_val) AS (
    -- Anchor: first track for each playlist
    SELECT 
      ot.playlist_id, 
      ot.track_id, 
      ot.rn, 
      '0|hzzzzz:'
    FROM OrderedTracks ot
    WHERE ot.rn = 1
    
    UNION ALL
    
    -- Recursive part: apply increment logic provided by user
    SELECT 
      ot.playlist_id, 
      ot.track_id, 
      ot.rn,
      (
        WITH Constants AS (
            SELECT 
                '0123456789abcdefghijklmnopqrstuvwxyz' AS charset,
                gr.rank_val AS current_rank
        ),
        Parsing AS (
            SELECT 
                charset,
                current_rank,
                SUBSTR(
                    current_rank,
                    INSTR(current_rank, '|') + 1,
                    INSTR(current_rank, ':') - INSTR(current_rank, '|') - 1
                ) AS rank_val
            FROM Constants
        ),
        Parsed AS (
            SELECT
                charset,
                rank_val,
                RTRIM(rank_val, 'z') AS base_part,
                LENGTH(rank_val) - LENGTH(RTRIM(rank_val, 'z')) AS trailing_z_count
            FROM Parsing
        ),
        IncrementLogic AS (
            SELECT
                charset,
                rank_val,
                base_part,
                trailing_z_count,
                CASE
                    WHEN base_part = '' 
                        THEN rank_val || 'i'
                    WHEN trailing_z_count > 0 THEN
                        SUBSTR(base_part, 1, LENGTH(base_part) - 1)
                        || SUBSTR(
                            charset,
                            INSTR(charset, SUBSTR(base_part, LENGTH(base_part), 1)) + 1,
                            1
                        )
                        || SUBSTR('000000000000000000000000000000000000', 1, trailing_z_count)
                    ELSE
                        SUBSTR(rank_val, 1, LENGTH(rank_val) - 1)
                        || SUBSTR(
                            charset,
                            INSTR(charset, SUBSTR(rank_val, LENGTH(rank_val), 1)) + 1,
                            1
                        )
                END AS next_rank_val
            FROM Parsed
        )
        SELECT '0|' || next_rank_val || ':' FROM IncrementLogic
      )
    FROM GeneratedRanks gr
    JOIN OrderedTracks ot ON gr.playlist_id = ot.playlist_id AND ot.rn = gr.rn + 1
  )
INSERT INTO playlist_tracks_new (playlist_id, track_id, position)
SELECT playlist_id, track_id, rank_val FROM GeneratedRanks;

-- Replace old table
DROP TABLE playlist_tracks;
ALTER TABLE playlist_tracks_new RENAME TO playlist_tracks;
