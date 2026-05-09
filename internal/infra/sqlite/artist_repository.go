package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"airmedy/internal/domain"
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

func (r *artistRepository) GetByNormalizationKey(ctx context.Context, key string) (*domain.Artist, error) {
	var artist domain.Artist
	err := r.db.GetContext(ctx, &artist, "SELECT * FROM artists WHERE normalization_key = ?", key)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get artist by normalization key: %w", err)
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
	if artist.CreatedAt.IsZero() {
		artist.CreatedAt = now
	}
	artist.UpdatedAt = now

	query := `
		INSERT INTO artists (
			id, name, sort_name, normalization_key, artwork_key, created_at, updated_at
		) VALUES (
			:id, :name, :sort_name, :normalization_key, :artwork_key, :created_at, :updated_at
		)`

	_, err := r.db.NamedExecContext(ctx, query, artist)
	if err != nil {
		return fmt.Errorf("failed to save artist: %w", err)
	}
	return nil
}

func (r *artistRepository) Upsert(ctx context.Context, artist *domain.Artist) error {
	now := time.Now()
	if artist.CreatedAt.IsZero() {
		artist.CreatedAt = now
	}
	artist.UpdatedAt = now

	query := `
		INSERT INTO artists (
			id, name, sort_name, normalization_key, artwork_key, created_at, updated_at
		) VALUES (
			:id, :name, :sort_name, :normalization_key, :artwork_key, :created_at, :updated_at
		) ON CONFLICT(id) DO UPDATE SET
			name = excluded.name,
			sort_name = excluded.sort_name,
			normalization_key = excluded.normalization_key,
			artwork_key = CASE 
				WHEN excluded.artwork_key IS NOT NULL THEN excluded.artwork_key 
				ELSE artists.artwork_key 
			END,
			updated_at = excluded.updated_at
	`

	_, err := r.db.NamedExecContext(ctx, query, artist)
	if err != nil {
		return fmt.Errorf("failed to upsert artist: %w", err)
	}
	return nil
}

func (r *artistRepository) DeleteOrphaned(ctx context.Context) error {
	// Clean up orphaned junction rows that might exist from before foreign keys were enabled
	_, _ = r.db.ExecContext(ctx, "DELETE FROM track_artists WHERE track_id NOT IN (SELECT id FROM tracks)")
	_, _ = r.db.ExecContext(ctx, "DELETE FROM track_album_artists WHERE track_id NOT IN (SELECT id FROM tracks)")
	_, _ = r.db.ExecContext(ctx, "DELETE FROM album_artists WHERE album_id NOT IN (SELECT id FROM albums)")

	query := `
		DELETE FROM artists
		WHERE id NOT IN (SELECT artist_id FROM track_artists)
		  AND id NOT IN (SELECT artist_id FROM track_album_artists)
		  AND id NOT IN (SELECT artist_id FROM album_artists)
	`
	_, err := r.db.ExecContext(ctx, query)
	if err != nil {
		return fmt.Errorf("failed to delete orphaned artists: %w", err)
	}
	return nil
}
