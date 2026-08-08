package sqlite

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"airmedy/internal/domain"
)

type mobileLibrarySyncPlanRepository struct{ db *DB }

func NewMobileLibrarySyncPlanRepository(db *DB) domain.MobileLibrarySyncPlanRepository {
	return &mobileLibrarySyncPlanRepository{db: db}
}

func (r *mobileLibrarySyncPlanRepository) GetLatest(ctx context.Context, deviceID string) (*domain.MobileLibrarySyncPlan, error) {
	var row struct {
		ID           string    `db:"id"`
		DeviceID     string    `db:"device_id"`
		Scope        string    `db:"scope_json"`
		Manifest     string    `db:"manifest_json"`
		ManifestHash string    `db:"manifest_hash"`
		Status       string    `db:"status"`
		Completed    int       `db:"completed"`
		Total        int       `db:"total"`
		CreatedAt    time.Time `db:"created_at"`
		UpdatedAt    time.Time `db:"updated_at"`
	}
	err := r.db.GetContext(ctx, &row, `SELECT id, device_id, scope_json, manifest_json, manifest_hash, status, completed, total, created_at, updated_at FROM mobile_library_sync_plans WHERE device_id = ? ORDER BY updated_at DESC LIMIT 1`, deviceID)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get latest mobile library sync plan: %w", err)
	}
	plan := &domain.MobileLibrarySyncPlan{ID: row.ID, DeviceID: row.DeviceID, ManifestHash: row.ManifestHash, Status: row.Status, Completed: row.Completed, Total: row.Total, CreatedAt: row.CreatedAt, UpdatedAt: row.UpdatedAt}
	if err := json.Unmarshal([]byte(row.Scope), &plan.Scope); err != nil {
		return nil, fmt.Errorf("decode mobile library sync scope: %w", err)
	}
	if err := json.Unmarshal([]byte(row.Manifest), &plan.Manifest); err != nil {
		return nil, fmt.Errorf("decode mobile library sync manifest: %w", err)
	}
	return plan, nil
}

func (r *mobileLibrarySyncPlanRepository) Save(ctx context.Context, plan *domain.MobileLibrarySyncPlan) error {
	scope, err := json.Marshal(plan.Scope)
	if err != nil {
		return fmt.Errorf("encode mobile library sync scope: %w", err)
	}
	manifest, err := json.Marshal(plan.Manifest)
	if err != nil {
		return fmt.Errorf("encode mobile library sync manifest: %w", err)
	}
	_, err = r.db.Ext(ctx).ExecContext(ctx, `INSERT INTO mobile_library_sync_plans (id, device_id, scope_json, manifest_json, manifest_hash, status, completed, total, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`, plan.ID, plan.DeviceID, scope, manifest, plan.ManifestHash, plan.Status, plan.Completed, plan.Total, plan.CreatedAt, plan.UpdatedAt)
	if err != nil {
		return fmt.Errorf("save mobile library sync plan: %w", err)
	}
	return nil
}

func (r *mobileLibrarySyncPlanRepository) MarkSuperseded(ctx context.Context, deviceID string) error {
	_, err := r.db.Ext(ctx).ExecContext(ctx, `UPDATE mobile_library_sync_plans SET status = 'superseded', updated_at = ? WHERE device_id = ? AND status = 'active'`, time.Now().UTC(), deviceID)
	if err != nil {
		return fmt.Errorf("supersede mobile library sync plan: %w", err)
	}
	return nil
}

func (r *mobileLibrarySyncPlanRepository) MarkReceipt(ctx context.Context, planID, assetID string, at time.Time) (int, error) {
	var completed int
	err := r.db.RunTx(ctx, func(ctx context.Context) error {
		ex := r.db.Ext(ctx)
		if _, err := ex.ExecContext(ctx, `INSERT OR IGNORE INTO mobile_library_sync_receipts (plan_id, asset_id, received_at) VALUES (?, ?, ?)`, planID, assetID, at); err != nil {
			return fmt.Errorf("save mobile sync receipt: %w", err)
		}
		if err := ex.GetContext(ctx, &completed, `SELECT COUNT(*) FROM mobile_library_sync_receipts WHERE plan_id = ?`, planID); err != nil {
			return fmt.Errorf("count mobile sync receipts: %w", err)
		}
		_, err := ex.ExecContext(ctx, `UPDATE mobile_library_sync_plans SET completed = ?, updated_at = ? WHERE id = ?`, completed, at, planID)
		return err
	})
	if err != nil {
		return 0, err
	}
	return completed, nil
}

func (r *mobileLibrarySyncPlanRepository) MarkComplete(ctx context.Context, planID string, at time.Time) error {
	_, err := r.db.Ext(ctx).ExecContext(ctx, `UPDATE mobile_library_sync_plans SET status = 'complete', completed = total, updated_at = ? WHERE id = ?`, at, planID)
	if err != nil {
		return fmt.Errorf("complete mobile library sync plan: %w", err)
	}
	return nil
}
