package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"changeme/internal/domain"
)

type albumRepository struct {
	db *DB
}

func NewAlbumRepository(db *DB) domain.AlbumRepository {
	return &albumRepository{db: db}
}

func (r *albumRepository) GetByID(ctx context.Context, id string) (*domain.Album, error) {
	var album domain.Album
	err := r.db.GetContext(ctx, &album, "SELECT * FROM albums WHERE id = ?", id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get album by id: %w", err)
	}
	return &album, nil
}

func (r *albumRepository) GetAll(ctx context.Context) ([]*domain.Album, error) {
	var albums []*domain.Album
	err := r.db.SelectContext(ctx, &albums, "SELECT * FROM albums ORDER BY sort_title")
	if err != nil {
		return nil, fmt.Errorf("failed to get all albums: %w", err)
	}
	return albums, nil
}

func (r *albumRepository) Save(ctx context.Context, album *domain.Album) error {
	now := time.Now()
	album.CreatedAt = now
	album.UpdatedAt = now

	query := `
		INSERT INTO albums (
			id, title, sort_title, artist_id, artist_name, year, artwork_key, created_at, updated_at
		) VALUES (
			:id, :title, :sort_title, :artist_id, :artist_name, :year, :artwork_key, :created_at, :updated_at
		)`

	_, err := r.db.NamedExecContext(ctx, query, album)
	if err != nil {
		return fmt.Errorf("failed to save album: %w", err)
	}
	return nil
}

func (r *albumRepository) Upsert(ctx context.Context, album *domain.Album) error {
	now := time.Now()
	album.UpdatedAt = now

	query := `
		INSERT INTO albums (
			id, title, sort_title, artist_id, artist_name, year, artwork_key, created_at, updated_at
		) VALUES (
			:id, :title, :sort_title, :artist_id, :artist_name, :year, :artwork_key, :created_at, :updated_at
		) ON CONFLICT(id) DO UPDATE SET
			title = excluded.title,
			sort_title = excluded.sort_title,
			artist_id = excluded.artist_id,
			artist_name = excluded.artist_name,
			year = excluded.year,
			artwork_key = excluded.artwork_key,
			updated_at = excluded.updated_at
	`

	_, err := r.db.NamedExecContext(ctx, query, album)
	if err != nil {
		return fmt.Errorf("failed to upsert album: %w", err)
	}
	return nil
}
