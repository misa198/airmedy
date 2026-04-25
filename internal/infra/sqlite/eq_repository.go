package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type eqRepository struct {
	db *DB
}

func NewEQRepository(db *DB) domain.EQRepository {
	return &eqRepository{db: db}
}

func (r *eqRepository) GetByID(ctx context.Context, id string) (*domain.EQProfile, error) {
	var p domain.EQProfile
	err := r.db.GetContext(ctx, &p, "SELECT id, name, is_active, is_default FROM eq_profiles WHERE id = ?", id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get eq profile: %w", err)
	}
	bands, err := r.getBands(ctx, id)
	if err != nil {
		return nil, err
	}
	p.Bands = bands
	return &p, nil
}

func (r *eqRepository) GetActive(ctx context.Context) (*domain.EQProfile, error) {
	var p domain.EQProfile
	err := r.db.GetContext(ctx, &p, "SELECT id, name, is_active, is_default FROM eq_profiles WHERE is_active = 1 LIMIT 1")
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get active eq profile: %w", err)
	}
	bands, err := r.getBands(ctx, p.ID)
	if err != nil {
		return nil, err
	}
	p.Bands = bands
	return &p, nil
}

func (r *eqRepository) GetAll(ctx context.Context) ([]*domain.EQProfile, error) {
	type row struct {
		ID        string `db:"id"`
		Name      string `db:"name"`
		IsActive  bool   `db:"is_active"`
		IsDefault bool   `db:"is_default"`
	}
	var rows []row
	if err := r.db.SelectContext(ctx, &rows, "SELECT id, name, is_active, is_default FROM eq_profiles ORDER BY created_at ASC"); err != nil {
		return nil, fmt.Errorf("failed to get all eq profiles: %w", err)
	}
	profiles := make([]*domain.EQProfile, len(rows))
	for i, row := range rows {
		bands, err := r.getBands(ctx, row.ID)
		if err != nil {
			return nil, err
		}
		profiles[i] = &domain.EQProfile{ID: row.ID, Name: row.Name, IsActive: row.IsActive, IsDefault: row.IsDefault, Bands: bands}
	}
	return profiles, nil
}

func (r *eqRepository) Save(ctx context.Context, p *domain.EQProfile) error {
	_, err := r.db.ExecContext(ctx,
		"INSERT OR REPLACE INTO eq_profiles (id, name, is_active, is_default) VALUES (?, ?, ?, ?)",
		p.ID, p.Name, p.IsActive, p.IsDefault,
	)
	if err != nil {
		return fmt.Errorf("failed to save eq profile: %w", err)
	}
	// Delete existing bands and re-insert.
	if _, err := r.db.ExecContext(ctx, "DELETE FROM eq_bands WHERE profile_id = ?", p.ID); err != nil {
		return fmt.Errorf("failed to clear eq bands: %w", err)
	}
	for _, band := range p.Bands {
		if _, err := r.db.ExecContext(ctx,
			"INSERT INTO eq_bands (profile_id, band_index, frequency, gain, bandwidth) VALUES (?, ?, ?, ?, ?)",
			p.ID, band.Index, band.Frequency, band.Gain, band.Bandwidth,
		); err != nil {
			return fmt.Errorf("failed to save eq band: %w", err)
		}
	}
	return nil
}

func (r *eqRepository) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM eq_profiles WHERE id = ?", id)
	return err
}

func (r *eqRepository) SetActive(ctx context.Context, id string) error {
	if _, err := r.db.ExecContext(ctx, "UPDATE eq_profiles SET is_active = 0"); err != nil {
		return err
	}
	_, err := r.db.ExecContext(ctx, "UPDATE eq_profiles SET is_active = 1 WHERE id = ?", id)
	return err
}

func (r *eqRepository) getBands(ctx context.Context, profileID string) ([]domain.EQBand, error) {
	type bandRow struct {
		Index     int     `db:"band_index"`
		Frequency float64 `db:"frequency"`
		Gain      float64 `db:"gain"`
		Bandwidth float64 `db:"bandwidth"`
	}
	var rows []bandRow
	if err := r.db.SelectContext(ctx, &rows,
		"SELECT band_index, frequency, gain, bandwidth FROM eq_bands WHERE profile_id = ? ORDER BY band_index",
		profileID,
	); err != nil {
		return nil, fmt.Errorf("failed to get eq bands: %w", err)
	}
	bands := make([]domain.EQBand, len(rows))
	for i, row := range rows {
		bands[i] = domain.EQBand{Index: row.Index, Frequency: row.Frequency, Gain: row.Gain, Bandwidth: row.Bandwidth}
	}
	return bands, nil
}
