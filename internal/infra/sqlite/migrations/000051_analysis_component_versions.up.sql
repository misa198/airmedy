-- Raw DSP sources are independently versioned so an algorithm change can
-- re-run only the source whose output changed.
CREATE TABLE IF NOT EXISTS track_analysis_components (
    track_id TEXT NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    component TEXT NOT NULL CHECK(component IN ('ffmpeg', 'aubio')),
    version INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL CHECK(status IN ('complete', 'failed')),
    analyzed_at DATETIME,
    PRIMARY KEY (track_id, component)
);

-- Version 4 is the last unified analyzer version. Only data at or above it
-- is known to match the current raw-feature schema and is therefore adopted
-- without a costly re-analysis. A legacy resolved track without a feature row
-- was a permanent failure; preserve that no-retry behaviour per component.
INSERT INTO track_analysis_components (track_id, component, version, status, analyzed_at)
SELECT t.id, c.component, 1,
       CASE WHEN tf.track_id IS NULL THEN 'failed' ELSE 'complete' END,
       tf.analyzed_at
FROM tracks t
CROSS JOIN (SELECT 'ffmpeg' AS component UNION ALL SELECT 'aubio') c
LEFT JOIN track_features tf ON tf.track_id = t.id
WHERE t.analyzed_version >= 4;
