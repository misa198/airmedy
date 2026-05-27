ALTER TABLE app_settings ADD COLUMN remote_server_enabled BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE app_settings ADD COLUMN remote_server_port INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_settings ADD COLUMN remote_server_password TEXT NOT NULL DEFAULT '';
