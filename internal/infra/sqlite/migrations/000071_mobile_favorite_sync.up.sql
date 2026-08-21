CREATE TABLE mobile_favorite_mutation_ledger (
    device_id TEXT NOT NULL REFERENCES paired_mobile_devices(device_id) ON DELETE CASCADE,
    mutation_id TEXT NOT NULL,
    result TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (device_id, mutation_id)
);

CREATE TABLE mobile_favorite_mutation_lww (
    track_id TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
    updated_at INTEGER NOT NULL,
    mutation_id TEXT NOT NULL,
    is_favorite INTEGER NOT NULL
);
