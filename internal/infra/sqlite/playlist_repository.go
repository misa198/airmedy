package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"airmedy/internal/domain"
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

	_, err := r.db.NamedExecContext(ctx, "INSERT INTO playlists (id, name, description, artwork_key, created_at, updated_at) VALUES (:id, :name, :description, :artwork_key, :created_at, :updated_at)", p)
	if err != nil {
		return fmt.Errorf("failed to save playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) Update(ctx context.Context, p *domain.Playlist) error {
	p.UpdatedAt = time.Now()
	_, err := r.db.ExecContext(ctx,
		"UPDATE playlists SET name = ?, description = ?, artwork_key = ?, updated_at = ? WHERE id = ?",
		p.Name, p.Description, p.ArtworkKey, p.UpdatedAt, p.ID,
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
	_, err := r.db.ExecContext(ctx, "INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)", playlistID, trackID, position)
	if err != nil {
		return fmt.Errorf("failed to add track to playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) RemoveTrack(ctx context.Context, playlistID, trackID string) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	// Get the position of the track being removed
	var pos int
	err = tx.GetContext(ctx, &pos, "SELECT position FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?", playlistID, trackID)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil // Track not in playlist
		}
		return err
	}

	// Remove the track
	_, err = tx.ExecContext(ctx, "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?", playlistID, trackID)
	if err != nil {
		return err
	}

	// Update positions of tracks after the removed one
	_, err = tx.ExecContext(ctx, "UPDATE playlist_tracks SET position = position - 1 WHERE playlist_id = ? AND position > ?", playlistID, pos)
	if err != nil {
		return err
	}

	return tx.Commit()
}

func (r *playlistRepository) GetTracks(ctx context.Context, playlistID string) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		JOIN playlist_tracks pt ON t.id = pt.track_id
		WHERE pt.playlist_id = ?
		GROUP BY t.id, pt.position
		ORDER BY pt.position`, trackSelectFields)
	
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, playlistID)
	if err != nil {
		return nil, fmt.Errorf("failed to get playlist tracks: %w", err)
	}

	tr := &trackRepository{db: r.db}
	return tr.scanTrackRows(rows), nil
}

func (r *playlistRepository) GetPlaylistsForTrack(ctx context.Context, trackID string) ([]string, error) {
	var ids []string
	query := "SELECT playlist_id FROM playlist_tracks WHERE track_id = ?"
	err := r.db.SelectContext(ctx, &ids, query, trackID)
	if err != nil {
		return nil, fmt.Errorf("failed to get playlists for track: %w", err)
	}
	return ids, nil
}
