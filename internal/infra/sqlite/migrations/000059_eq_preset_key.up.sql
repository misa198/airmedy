ALTER TABLE eq_profiles ADD COLUMN preset_key TEXT NOT NULL DEFAULT '';
CREATE UNIQUE INDEX idx_eq_profiles_preset_key
    ON eq_profiles(preset_key)
    WHERE preset_key != '';
