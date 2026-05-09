package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"airmedy/internal/domain"
)

type lyricRepository struct {
	db *DB
}

func NewLyricRepository(db *DB) domain.LyricRepository {
	return &lyricRepository{db: db}
}

func (r *lyricRepository) GetByTrackID(ctx context.Context, trackID string) (*domain.Lyric, error) {
	var l domain.Lyric
	query := fmt.Sprintf("SELECT %s FROM lyrics WHERE track_id = ?", lyricSelectFields)
	err := r.db.GetContext(ctx, &l, query, trackID)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get lyric by track id: %w", err)
	}
	return &l, nil
}

func (r *lyricRepository) Save(ctx context.Context, l *domain.Lyric) error {
	now := time.Now()
	if l.CreatedAt.IsZero() {
		l.CreatedAt = now
	}
	l.UpdatedAt = now

	_, err := r.db.NamedExecContext(ctx, "INSERT INTO lyrics (track_id, content, source, meta_content, meta_source, created_at, updated_at) VALUES (:track_id, :content, :source, :meta_content, :meta_source, :created_at, :updated_at)", l)
	if err != nil {
		return fmt.Errorf("failed to save lyric: %w", err)
	}
	return nil
}

func (r *lyricRepository) Upsert(ctx context.Context, l *domain.Lyric) error {
	now := time.Now()
	if l.CreatedAt.IsZero() {
		l.CreatedAt = now
	}
	l.UpdatedAt = now

	query := `
		INSERT INTO lyrics (track_id, content, source, meta_content, meta_source, created_at, updated_at)
		VALUES (:track_id, :content, :source, :meta_content, :meta_source, :created_at, :updated_at)
		ON CONFLICT(track_id) DO UPDATE SET
			content = excluded.content,
			source = excluded.source,
			meta_content = excluded.meta_content,
			meta_source = excluded.meta_source,
			updated_at = excluded.updated_at
	`
	_, err := r.db.NamedExecContext(ctx, query, l)
	if err != nil {
		return fmt.Errorf("failed to upsert lyric: %w", err)
	}
	return nil
}

func (r *lyricRepository) Delete(ctx context.Context, trackID string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM lyrics WHERE track_id = ?", trackID)
	if err != nil {
		return fmt.Errorf("failed to delete lyric: %w", err)
	}
	return nil
}
