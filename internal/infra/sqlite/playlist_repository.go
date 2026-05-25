package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	lexorank "github.com/misa198/lexorank-go"

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

func (r *playlistRepository) AddTrack(ctx context.Context, playlistID, trackID string, position string) error {
	_, err := r.db.ExecContext(ctx, "INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)", playlistID, trackID, position)
	if err != nil {
		return fmt.Errorf("failed to add track to playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) AddTracks(ctx context.Context, playlistID string, trackIDs []string) error {
	if len(trackIDs) == 0 {
		return nil
	}

	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	var maxRankStr string
	_ = tx.GetContext(ctx, &maxRankStr, "SELECT COALESCE(MAX(position), '') FROM playlist_tracks WHERE playlist_id = ?", playlistID)

	var currentRank lexorank.Rank
	if maxRankStr == "" {
		currentRank = lexorank.Middle()
	} else {
		maxRank, err := lexorank.ParseRank(maxRankStr)
		if err != nil {
			return fmt.Errorf("failed to parse max rank: %w", err)
		}
		currentRank = maxRank.GenNext()
	}

	placeholders := make([]string, len(trackIDs))
	args := make([]any, 0, len(trackIDs)*3)
	for i, trackID := range trackIDs {
		placeholders[i] = "(?, ?, ?)"
		args = append(args, playlistID, trackID, currentRank.String())
		currentRank = currentRank.GenNext()
	}

	query := "INSERT OR IGNORE INTO playlist_tracks (playlist_id, track_id, position) VALUES " + strings.Join(placeholders, ", ")
	if _, err = tx.ExecContext(ctx, query, args...); err != nil {
		return fmt.Errorf("failed to add tracks to playlist: %w", err)
	}

	return tx.Commit()
}

func (r *playlistRepository) RemoveTrack(ctx context.Context, playlistID, trackID string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?", playlistID, trackID)
	if err != nil {
		return fmt.Errorf("failed to remove track from playlist: %w", err)
	}
	return nil
}

func (r *playlistRepository) UpdateTrackPosition(ctx context.Context, playlistID, trackID, position string) error {
	_, err := r.db.ExecContext(ctx, "UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND track_id = ?", position, playlistID, trackID)
	if err != nil {
		return fmt.Errorf("failed to update track position: %w", err)
	}
	return nil
}

func (r *playlistRepository) UpdateTracksPositions(ctx context.Context, playlistID string, updates map[string]string) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	for trackID, position := range updates {
		_, err = tx.ExecContext(ctx, "UPDATE playlist_tracks SET position = ? WHERE playlist_id = ? AND track_id = ?", position, playlistID, trackID)
		if err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (r *playlistRepository) GetTrackPosition(ctx context.Context, playlistID, trackID string) (string, error) {
	var position string
	err := r.db.GetContext(ctx, &position, "SELECT position FROM playlist_tracks WHERE playlist_id = ? AND track_id = ?", playlistID, trackID)
	if err != nil {
		return "", err
	}
	return position, nil
}

func (r *playlistRepository) GetMaxPosition(ctx context.Context, playlistID string) (string, error) {
	var position string
	err := r.db.GetContext(ctx, &position, "SELECT COALESCE(MAX(position), '') FROM playlist_tracks WHERE playlist_id = ?", playlistID)
	if err != nil {
		if err == sql.ErrNoRows {
			return "", nil
		}
		return "", err
	}
	return position, nil
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
		ORDER BY pt.position, pt.track_id`, trackSelectFields)
	
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
