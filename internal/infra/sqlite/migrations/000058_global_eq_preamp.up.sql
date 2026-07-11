ALTER TABLE app_settings ADD COLUMN eq_preamp REAL NOT NULL DEFAULT 0;

UPDATE app_settings
SET eq_preamp = COALESCE((
    SELECT preamp_gain
    FROM eq_profiles
    WHERE is_active = 1
    LIMIT 1
), 0)
WHERE id = 1;

ALTER TABLE eq_profiles DROP COLUMN preamp_gain;
