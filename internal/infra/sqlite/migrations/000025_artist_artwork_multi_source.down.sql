ALTER TABLE artists ADD COLUMN artwork_key TEXT;
ALTER TABLE artists ADD COLUMN artwork_source TEXT NOT NULL DEFAULT '';

UPDATE artists SET artwork_key = artwork_key_manual, artwork_source = 'manual' WHERE artwork_key_manual IS NOT NULL;
UPDATE artists SET artwork_key = artwork_key_local, artwork_source = 'local_file' WHERE artwork_key IS NULL AND artwork_key_local IS NOT NULL;
UPDATE artists SET artwork_key = artwork_key_online, artwork_source = 'online' WHERE artwork_key IS NULL AND artwork_key_online IS NOT NULL;

ALTER TABLE artists DROP COLUMN artwork_key_manual;
ALTER TABLE artists DROP COLUMN artwork_key_local;
ALTER TABLE artists DROP COLUMN artwork_key_online;
