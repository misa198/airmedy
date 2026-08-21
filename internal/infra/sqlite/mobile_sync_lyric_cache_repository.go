package sqlite

import (
	"context"
	"fmt"
	"time"

	"airmedy/internal/domain"
	"github.com/jmoiron/sqlx"
)

type mobileSyncLyricCacheRepository struct{ db *DB }

func NewMobileSyncLyricCacheRepository(db *DB) domain.MobileSyncLyricCacheRepository {
	return &mobileSyncLyricCacheRepository{db: db}
}

func (r *mobileSyncLyricCacheRepository) GetByTrackIDs(ctx context.Context, ids []string) (map[string]*domain.MobileSyncLyricCache, error) {
	result := make(map[string]*domain.MobileSyncLyricCache, len(ids))
	for start := 0; start < len(ids); start += 900 {
		end := start + 900
		if end > len(ids) {
			end = len(ids)
		}
		query, args, err := sqlx.In("SELECT track_id, content, source, has_lyric, version, fingerprint, updated_at FROM mobile_sync_lyric_cache WHERE track_id IN (?)", ids[start:end])
		if err != nil {
			return nil, fmt.Errorf("build mobile lyric cache query: %w", err)
		}
		query = r.db.Rebind(query)
		var rows []domain.MobileSyncLyricCache
		if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
			return nil, fmt.Errorf("get mobile lyric cache: %w", err)
		}
		for i := range rows {
			result[rows[i].TrackID] = &rows[i]
		}
	}
	return result, nil
}

func (r *mobileSyncLyricCacheRepository) Upsert(ctx context.Context, entry *domain.MobileSyncLyricCache) error {
	entry.UpdatedAt = time.Now().UTC()
	_, err := r.db.NamedExecContext(ctx, `INSERT INTO mobile_sync_lyric_cache (track_id, content, source, has_lyric, version, fingerprint, updated_at)
		VALUES (:track_id, :content, :source, :has_lyric, :version, :fingerprint, :updated_at)
		ON CONFLICT(track_id) DO UPDATE SET content=excluded.content, source=excluded.source, has_lyric=excluded.has_lyric, version=excluded.version, fingerprint=excluded.fingerprint, updated_at=excluded.updated_at`, entry)
	if err != nil {
		return fmt.Errorf("upsert mobile lyric cache: %w", err)
	}
	return nil
}
