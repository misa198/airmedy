ALTER TABLE artists ADD COLUMN artwork_key_manual TEXT;
ALTER TABLE artists ADD COLUMN artwork_key_local TEXT;
ALTER TABLE artists ADD COLUMN artwork_key_online TEXT;

UPDATE artists SET artwork_key_manual = artwork_key WHERE artwork_source = 'manual' AND artwork_key IS NOT NULL;
UPDATE artists SET artwork_key_local = artwork_key WHERE artwork_source = 'local_file' AND artwork_key IS NOT NULL;
UPDATE artists SET artwork_key_online = artwork_key WHERE artwork_source = 'online' AND artwork_key IS NOT NULL;

ALTER TABLE artists DROP COLUMN artwork_key;
ALTER TABLE artists DROP COLUMN artwork_source;
