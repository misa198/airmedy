package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"changeme/internal/domain"
)

type trackRepository struct {
	db *DB
}

func NewTrackRepository(db *DB) domain.TrackRepository {
	return &trackRepository{db: db}
}

func (r *trackRepository) GetByID(ctx context.Context, id string) (*domain.Track, error) {
	var track domain.Track
	err := r.db.GetContext(ctx, &track, "SELECT * FROM tracks WHERE id = ?", id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get track by id: %w", err)
	}
	return &track, nil
}

func (r *trackRepository) GetByPath(ctx context.Context, path string) (*domain.Track, error) {
	var track domain.Track
	err := r.db.GetContext(ctx, &track, "SELECT * FROM tracks WHERE path = ?", path)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get track by path: %w", err)
	}
	return &track, nil
}

func (r *trackRepository) GetAll(ctx context.Context) ([]*domain.Track, error) {
	var tracks []*domain.Track
	err := r.db.SelectContext(ctx, &tracks, "SELECT * FROM tracks ORDER BY sort_artist_name, sort_album_name, disc_number, track_number")
	if err != nil {
		return nil, fmt.Errorf("failed to get all tracks: %w", err)
	}
	return tracks, nil
}

func (r *trackRepository) Save(ctx context.Context, track *domain.Track) error {
	now := time.Now()
	track.CreatedAt = now
	track.UpdatedAt = now

	query := `
		INSERT INTO tracks (
			id, path, title, sort_title, artist_id, artist_name, sort_artist_name,
			album_id, album_name, sort_album_name, album_artist_id, album_artist_name, sort_album_artist_name,
			genre_id, genre_name, composer_id, composer_name,
			year, track_number, total_tracks, disc_number, total_discs,
			duration, bitrate, sample_rate, format, artwork_key, created_at, updated_at
		) VALUES (
			:id, :path, :title, :sort_title, :artist_id, :artist_name, :sort_artist_name,
			:album_id, :album_name, :sort_album_name, :album_artist_id, :album_artist_name, :sort_album_artist_name,
			:genre_id, :genre_name, :composer_id, :composer_name,
			:year, :track_number, :total_tracks, :disc_number, :total_discs,
			:duration, :bitrate, :sample_rate, :format, :artwork_key, :created_at, :updated_at
		)`

	_, err := r.db.NamedExecContext(ctx, query, track)
	if err != nil {
		return fmt.Errorf("failed to save track: %w", err)
	}
	return nil
}

func (r *trackRepository) Upsert(ctx context.Context, track *domain.Track) error {
	now := time.Now()
	track.UpdatedAt = now

	query := `
		INSERT INTO tracks (
			id, path, title, sort_title, artist_id, artist_name, sort_artist_name,
			album_id, album_name, sort_album_name, album_artist_id, album_artist_name, sort_album_artist_name,
			genre_id, genre_name, composer_id, composer_name,
			year, track_number, total_tracks, disc_number, total_discs,
			duration, bitrate, sample_rate, format, artwork_key, created_at, updated_at
		) VALUES (
			:id, :path, :title, :sort_title, :artist_id, :artist_name, :sort_artist_name,
			:album_id, :album_name, :sort_album_name, :album_artist_id, :album_artist_name, :sort_album_artist_name,
			:genre_id, :genre_name, :composer_id, :composer_name,
			:year, :track_number, :total_tracks, :disc_number, :total_discs,
			:duration, :bitrate, :sample_rate, :format, :artwork_key, :created_at, :updated_at
		) ON CONFLICT(path) DO UPDATE SET
			title = excluded.title,
			sort_title = excluded.sort_title,
			artist_id = excluded.artist_id,
			artist_name = excluded.artist_name,
			sort_artist_name = excluded.sort_artist_name,
			album_id = excluded.album_id,
			album_name = excluded.album_name,
			sort_album_name = excluded.sort_album_name,
			album_artist_id = excluded.album_artist_id,
			album_artist_name = excluded.album_artist_name,
			sort_album_artist_name = excluded.sort_album_artist_name,
			genre_id = excluded.genre_id,
			genre_name = excluded.genre_name,
			composer_id = excluded.composer_id,
			composer_name = excluded.composer_name,
			year = excluded.year,
			track_number = excluded.track_number,
			total_tracks = excluded.total_tracks,
			disc_number = excluded.disc_number,
			total_discs = excluded.total_discs,
			duration = excluded.duration,
			bitrate = excluded.bitrate,
			sample_rate = excluded.sample_rate,
			format = excluded.format,
			artwork_key = excluded.artwork_key,
			updated_at = excluded.updated_at
	`

	_, err := r.db.NamedExecContext(ctx, query, track)
	if err != nil {
		return fmt.Errorf("failed to upsert track: %w", err)
	}
	return nil
}

func (r *trackRepository) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM tracks WHERE id = ?", id)
	if err != nil {
		return fmt.Errorf("failed to delete track: %w", err)
	}
	return nil
}
