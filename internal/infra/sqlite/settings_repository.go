package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type settingsRepository struct {
	db *DB
}

func NewSettingsRepository(db *DB) domain.SettingsRepository {
	return &settingsRepository{db: db}
}

func (r *settingsRepository) Save(ctx context.Context, settings *domain.AppSettings) error {
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO app_settings (id, language, updated_at)
		 VALUES (1, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   language = excluded.language,
		   updated_at = excluded.updated_at`,
		settings.Language,
	)
	if err != nil {
		return fmt.Errorf("failed to save app settings: %w", err)
	}
	return nil
}

func (r *settingsRepository) Load(ctx context.Context) (*domain.AppSettings, error) {
	var language string
	err := r.db.GetContext(ctx, &language,
		`SELECT language FROM app_settings WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return &domain.AppSettings{Language: "en"}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load app settings: %w", err)
	}

	return &domain.AppSettings{
		Language: language,
	}, nil
}
