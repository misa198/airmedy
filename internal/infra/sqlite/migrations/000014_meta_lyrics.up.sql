-- Add metadata lyrics columns to lyrics table
ALTER TABLE lyrics ADD COLUMN meta_content TEXT NOT NULL DEFAULT '';
ALTER TABLE lyrics ADD COLUMN meta_source TEXT NOT NULL DEFAULT '';

-- Add lrclib mode setting
ALTER TABLE app_settings ADD COLUMN lrclib_mode TEXT NOT NULL DEFAULT 'prefer_metadata';

-- Backfill: insert lyrics rows for tracks that have LYRICS in other_metadata but no lyrics row yet
INSERT OR IGNORE INTO lyrics (track_id, content, source, meta_content, meta_source, created_at, updated_at)
SELECT
    t.id,
    '', '',
    json_extract(t.other_metadata, '$.LYRICS[0]'),
    CASE
        WHEN json_extract(t.other_metadata, '$.LYRICS[0]') LIKE '[%:%.%]%'
        THEN 'meta-synced'
        ELSE 'meta-plain'
    END,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM tracks t
WHERE json_extract(t.other_metadata, '$.LYRICS[0]') IS NOT NULL
  AND json_extract(t.other_metadata, '$.LYRICS[0]') != ''
  AND NOT EXISTS (SELECT 1 FROM lyrics l WHERE l.track_id = t.id);

-- Backfill: update existing lyrics rows that have no meta_content yet
UPDATE lyrics
SET meta_content = json_extract(tracks.other_metadata, '$.LYRICS[0]'),
    meta_source = CASE
        WHEN json_extract(tracks.other_metadata, '$.LYRICS[0]') LIKE '[%:%.%]%'
        THEN 'meta-synced'
        ELSE 'meta-plain'
    END,
    updated_at = CURRENT_TIMESTAMP
FROM tracks
WHERE lyrics.track_id = tracks.id
  AND lyrics.meta_content = ''
  AND json_extract(tracks.other_metadata, '$.LYRICS[0]') IS NOT NULL
  AND json_extract(tracks.other_metadata, '$.LYRICS[0]') != '';
