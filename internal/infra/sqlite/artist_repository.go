package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"changeme/internal/domain"
)

type artistRepository struct {
	db *DB
}

func NewArtistRepository(db *DB) domain.ArtistRepository {
	return &artistRepository{db: db}
}

func (r *artistRepository) GetByID(ctx context.Context, id string) (*domain.Artist, error) {
	var artist domain.Artist
	err := r.db.GetContext(ctx, &artist, "SELECT * FROM artists WHERE id = ?", id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get artist by id: %w", err)
	}
	return &artist, nil
}

func (r *artistRepository) GetAll(ctx context.Context) ([]*domain.Artist, error) {
	var artists []*domain.Artist
	err := r.db.SelectContext(ctx, &artists, "SELECT * FROM artists ORDER BY sort_name")
	if err != nil {
		return nil, fmt.Errorf("failed to get all artists: %w", err)
	}
	return artists, nil
}

func (r *artistRepository) Save(ctx context.Context, artist *domain.Artist) error {
	now := time.Now()
	artist.CreatedAt = now
	artist.UpdatedAt = now

	query := `
		INSERT INTO artists (
			id, name, sort_name, created_at, updated_at
		) VALUES (
			:id, :name, :sort_name, :created_at, :updated_at
		)`

	_, err := r.db.NamedExecContext(ctx, query, artist)
	if err != nil {
		return fmt.Errorf("failed to save artist: %w", err)
	}
	return nil
}

func (r *artistRepository) Upsert(ctx context.Context, artist *domain.Artist) error {
	now := time.Now()
	artist.UpdatedAt = now

	query := `
		INSERT INTO artists (
			id, name, sort_name, created_at, updated_at
		) VALUES (
			:id, :name, :sort_name, :created_at, :updated_at
		) ON CONFLICT(id) DO UPDATE SET
			name = excluded.name,
			sort_name = excluded.sort_name,
			updated_at = excluded.updated_at
	`

	_, err := r.db.NamedExecContext(ctx, query, artist)
	if err != nil {
		return fmt.Errorf("failed to upsert artist: %w", err)
	}
	return nil
}
