-- SQLite doesn't support DROP COLUMN easily before 3.35.0, 
-- but we can use ALTER TABLE RENAME/RECREATE if needed.
-- For simple migrations, we can just leave them or try DROP if supported.
ALTER TABLE tracks DROP COLUMN bpm;
ALTER TABLE tracks DROP COLUMN label;
ALTER TABLE tracks DROP COLUMN isrc;
ALTER TABLE tracks DROP COLUMN play_count;
