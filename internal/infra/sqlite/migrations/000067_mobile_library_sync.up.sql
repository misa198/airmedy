CREATE TABLE mobile_library_sync_plans (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES paired_mobile_devices(device_id) ON DELETE CASCADE,
    scope_json TEXT NOT NULL,
    manifest_json TEXT NOT NULL,
    manifest_hash TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('active', 'complete', 'superseded', 'failed')),
    completed INTEGER NOT NULL DEFAULT 0,
    total INTEGER NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE INDEX idx_mobile_library_sync_plans_device_updated
    ON mobile_library_sync_plans(device_id, updated_at DESC);

CREATE TABLE mobile_library_sync_receipts (
    plan_id TEXT NOT NULL REFERENCES mobile_library_sync_plans(id) ON DELETE CASCADE,
    asset_id TEXT NOT NULL,
    received_at DATETIME NOT NULL,
    PRIMARY KEY (plan_id, asset_id)
);
