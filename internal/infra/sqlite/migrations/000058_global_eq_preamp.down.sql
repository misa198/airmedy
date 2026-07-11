ALTER TABLE eq_profiles ADD COLUMN preamp_gain REAL NOT NULL DEFAULT 0;

UPDATE eq_profiles
SET preamp_gain = COALESCE((
    SELECT eq_preamp
    FROM app_settings
    WHERE id = 1
), 0);

ALTER TABLE app_settings DROP COLUMN eq_preamp;
