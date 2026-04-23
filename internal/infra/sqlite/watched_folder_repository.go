package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"airmedy/internal/domain"
)

type watchedFolderRepository struct {
	db *DB
}

func NewWatchedFolderRepository(db *DB) domain.WatchedFolderRepository {
	return &watchedFolderRepository{db: db}
}

func (r *watchedFolderRepository) GetByID(ctx context.Context, id string) (*domain.WatchedFolder, error) {
	var folder domain.WatchedFolder
	err := r.db.GetContext(ctx, &folder, "SELECT * FROM watched_folders WHERE id = ?", id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get watched folder by id: %w", err)
	}
	return &folder, nil
}

func (r *watchedFolderRepository) GetAll(ctx context.Context) ([]*domain.WatchedFolder, error) {
	var folders []*domain.WatchedFolder
	err := r.db.SelectContext(ctx, &folders, "SELECT * FROM watched_folders ORDER BY created_at DESC")
	if err != nil {
		return nil, fmt.Errorf("failed to get all watched folders: %w", err)
	}
	return folders, nil
}

func (r *watchedFolderRepository) Save(ctx context.Context, folder *domain.WatchedFolder) error {
	if folder.CreatedAt.IsZero() {
		folder.CreatedAt = time.Now()
	}

	query := `
		INSERT INTO watched_folders (id, path, created_at)
		VALUES (:id, :path, :created_at)
		ON CONFLICT(path) DO UPDATE SET
			created_at = excluded.created_at
	`

	_, err := r.db.NamedExecContext(ctx, query, folder)
	if err != nil {
		return fmt.Errorf("failed to save watched folder: %w", err)
	}
	return nil
}

func (r *watchedFolderRepository) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM watched_folders WHERE id = ?", id)
	if err != nil {
		return fmt.Errorf("failed to delete watched folder: %w", err)
	}
	return nil
}
