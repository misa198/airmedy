ALTER TABLE eq_profiles ADD COLUMN is_default INTEGER NOT NULL DEFAULT 0;
UPDATE eq_profiles SET is_default = 1;
