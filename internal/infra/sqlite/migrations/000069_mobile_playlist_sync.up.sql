CREATE TABLE mobile_playlist_mutation_ledger (
    device_id TEXT NOT NULL REFERENCES paired_mobile_devices(device_id) ON DELETE CASCADE,
    mutation_id TEXT NOT NULL,
    result TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (device_id, mutation_id)
);
