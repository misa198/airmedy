package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"airmedy/internal/domain"
	"github.com/jmoiron/sqlx"
)

type lyricRepository struct {
	db *DB
}

func (r *lyricRepository) GetByTrackIDs(ctx context.Context, trackIDs []string) (map[string]*domain.Lyric, error) {
	result := make(map[string]*domain.Lyric, len(trackIDs))
	for start := 0; start < len(trackIDs); start += 900 {
		end := start + 900
		if end > len(trackIDs) {
			end = len(trackIDs)
		}
		query, args, err := sqlx.In("SELECT "+lyricSelectFields+" FROM lyrics WHERE track_id IN (?)", trackIDs[start:end])
		if err != nil {
			return nil, fmt.Errorf("build lyrics batch query: %w", err)
		}
		query = r.db.Rebind(query)
		var rows []domain.Lyric
		if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
			return nil, fmt.Errorf("get lyrics by track ids: %w", err)
		}
		for i := range rows {
			result[rows[i].TrackID] = &rows[i]
		}
	}
	return result, nil
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
