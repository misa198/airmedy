package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"changeme/internal/domain"
)

type playlistRepository struct {
	db *DB
}

func NewPlaylistRepository(db *DB) domain.PlaylistRepository {
	return &playlistRepository{db: db}
}

func (r *playlistRepository) GetByID(ctx context.Context, id string) (*domain.Playlist, error) {
	var p domain.Playlist
	query := fmt.Sprintf("SELECT %s FROM playlists WHERE id = ?", playlistSelectFields)
	err := r.db.GetContext(ctx, &p, query, id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get playlist by id: %w", err)
	}
	return &p, nil
}

func (r *playlistRepository) GetAll(ctx context.Context) ([]*domain.Playlist, error) {
	var playlists []*domain.Playlist
	query := fmt.Sprintf("SELECT %s FROM playlists ORDER BY name", playlistSelectFields)
	err := r.db.SelectContext(ctx, &playlists, query)
	if err != nil {
		return nil, fmt.Errorf("failed to get all playlists: %w", err)
	}
	return playlists, nil
}

func (r *playlistRepository) Save(ctx context.Context, p *domain.Playlist) error {
	now := time.Now()
	p.CreatedAt = now
	p.UpdatedAt = now

	_, err := r.db.NamedExecContext(ctx, "INSERT INTO playlists (id, name, description, created_at, updated_at) VALUES (:id, :name, :description, :created_at, :updated_at)", p)
	if err != nil {
		return fmt.Errorf("failed to save playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) Update(ctx context.Context, p *domain.Playlist) error {
	p.UpdatedAt = time.Now()
	_, err := r.db.ExecContext(ctx,
		"UPDATE playlists SET name = ?, description = ?, updated_at = ? WHERE id = ?",
		p.Name, p.Description, p.UpdatedAt, p.ID,
	)
	if err != nil {
		return fmt.Errorf("failed to update playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) CountTracks(ctx context.Context, playlistID string) (int, error) {
	var count int
	err := r.db.GetContext(ctx, &count, "SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = ?", playlistID)
	if err != nil {
		return 0, fmt.Errorf("failed to count playlist tracks: %w", err)
	}
	return count, nil
}

func (r *playlistRepository) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM playlists WHERE id = ?", id)
	if err != nil {
		return fmt.Errorf("failed to delete playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) AddTrack(ctx context.Context, playlistID, trackID string, position int) error {
	_, err := r.db.ExecContext(ctx, "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)", playlistID, trackID, position)
	if err != nil {
		return fmt.Errorf("failed to add track to playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) RemoveTrack(ctx context.Context, playlistID, trackID string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?", playlistID, trackID)
	if err != nil {
		return fmt.Errorf("failed to remove track from playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) GetTracks(ctx context.Context, playlistID string) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s FROM tracks t
		JOIN playlist_tracks pt ON t.id = pt.track_id
		WHERE pt.playlist_id = ?
		ORDER BY pt.position`, trackSelectFields)
	
	var tracks []domain.Track
	err := r.db.SelectContext(ctx, &tracks, query, playlistID)
	if err != nil {
		return nil, fmt.Errorf("failed to get playlist tracks: %w", err)
	}

	tr := &trackRepository{db: r.db}
	dtos := make([]*domain.TrackDTO, len(tracks))
	for i, track := range tracks {
		dto := &domain.TrackDTO{Track: track}
		tr.populateRelationships(ctx, dto)
		dtos[i] = dto
	}
	return dtos, nil
}
