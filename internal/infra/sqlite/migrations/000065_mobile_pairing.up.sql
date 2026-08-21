CREATE TABLE pairing_identity (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    device_id TEXT NOT NULL UNIQUE,
    public_key BLOB NOT NULL CHECK (length(public_key) = 32),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE paired_mobile_devices (
    device_id TEXT PRIMARY KEY,
    public_key BLOB NOT NULL UNIQUE CHECK (length(public_key) = 32),
    display_name TEXT NOT NULL,
    platform TEXT NOT NULL,
    paired_at DATETIME NOT NULL,
    last_seen_at DATETIME NOT NULL
);

CREATE INDEX idx_paired_mobile_devices_last_seen_at ON paired_mobile_devices(last_seen_at DESC);
