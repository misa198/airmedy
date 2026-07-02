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
	err := r.db.Ext(ctx).GetContext(ctx, &artist, "SELECT * FROM artists WHERE id = ?", id)
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
	err := r.db.Ext(ctx).GetContext(ctx, &artist, "SELECT * FROM artists WHERE normalization_key = ?", key)
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
	err := r.db.Ext(ctx).SelectContext(ctx, &artists, "SELECT * FROM artists ORDER BY sort_name")
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
			id, name, sort_name, normalization_key, created_at, updated_at
		) VALUES (
			:id, :name, :sort_name, :normalization_key, :created_at, :updated_at
		)`

	_, err := r.db.Ext(ctx).NamedExecContext(ctx, query, artist)
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

	// Artwork columns are intentionally not touched here — they are managed
	// separately via SetArtworkSource so a metadata re-import never clobbers them.
	query := `
		INSERT INTO artists (
			id, name, sort_name, normalization_key, created_at, updated_at
		) VALUES (
			:id, :name, :sort_name, :normalization_key, :created_at, :updated_at
		) ON CONFLICT(id) DO UPDATE SET
			name = excluded.name,
			sort_name = excluded.sort_name,
			normalization_key = excluded.normalization_key,
			updated_at = excluded.updated_at
	`

	_, err := r.db.Ext(ctx).NamedExecContext(ctx, query, artist)
	if err != nil {
		return fmt.Errorf("failed to upsert artist: %w", err)
	}
	return nil
}

// artworkColumnForSource maps an artwork source to its DB column.
func artworkColumnForSource(source string) string {
	switch source {
	case domain.ArtworkSourceManual:
		return "artwork_key_manual"
	case domain.ArtworkSourceLocalFile:
		return "artwork_key_local"
	case domain.ArtworkSourceOnline:
		return "artwork_key_online"
	default:
		return ""
	}
}

func (r *artistRepository) SetArtworkSource(ctx context.Context, id string, source string, key *string) error {
	col := artworkColumnForSource(source)
	if col == "" {
		return fmt.Errorf("unknown artwork source: %q", source)
	}
	query := fmt.Sprintf("UPDATE artists SET %s = ?, updated_at = ? WHERE id = ?", col)
	if _, err := r.db.Ext(ctx).ExecContext(ctx, query, key, time.Now(), id); err != nil {
		return fmt.Errorf("failed to set artist artwork (%s): %w", source, err)
	}
	return nil
}

func (r *artistRepository) GetAllArtworkKeys(ctx context.Context) ([]string, error) {
	var keys []string
	err := r.db.Ext(ctx).SelectContext(ctx, &keys, `
		SELECT artwork_key_manual FROM artists WHERE artwork_key_manual IS NOT NULL AND artwork_key_manual != ''
		UNION
		SELECT artwork_key_local FROM artists WHERE artwork_key_local IS NOT NULL AND artwork_key_local != ''
		UNION
		SELECT artwork_key_online FROM artists WHERE artwork_key_online IS NOT NULL AND artwork_key_online != ''
	`)
	if err != nil {
		return nil, fmt.Errorf("failed to get all artist artwork keys: %w", err)
	}
	return keys, nil
}

func (r *artistRepository) SetArtwork(ctx context.Context, id string, key *string, source string) error {
	_, err := r.db.Ext(ctx).ExecContext(ctx,
		"UPDATE artists SET artwork_key = ?, artwork_source = ?, updated_at = ? WHERE id = ?",
		key, source, time.Now(), id,
	)
	if err != nil {
		return fmt.Errorf("failed to set artist artwork: %w", err)
	}
	return nil
}

func (r *artistRepository) DeleteOrphaned(ctx context.Context) ([]string, error) {
	// Clean up orphaned junction rows that might exist from before foreign keys were enabled
	_, _ = r.db.Ext(ctx).ExecContext(ctx, "DELETE FROM track_artists WHERE track_id NOT IN (SELECT id FROM tracks)")
	_, _ = r.db.Ext(ctx).ExecContext(ctx, "DELETE FROM track_album_artists WHERE track_id NOT IN (SELECT id FROM tracks)")
	_, _ = r.db.Ext(ctx).ExecContext(ctx, "DELETE FROM album_artists WHERE album_id NOT IN (SELECT id FROM albums)")

	const cond = `id NOT IN (SELECT artist_id FROM track_artists)
		  AND id NOT IN (SELECT artist_id FROM track_album_artists)
		  AND id NOT IN (SELECT artist_id FROM album_artists)`
	var ids []string
	if err := r.db.Ext(ctx).SelectContext(ctx, &ids, `SELECT id FROM artists WHERE `+cond); err != nil {
		return nil, fmt.Errorf("failed to select orphaned artists: %w", err)
	}
	if _, err := r.db.Ext(ctx).ExecContext(ctx, `DELETE FROM artists WHERE `+cond); err != nil {
		return nil, fmt.Errorf("failed to delete orphaned artists: %w", err)
	}
	return ids, nil
}
