package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"airmedy/internal/domain"
)

type albumRepository struct {
	db *DB
}

func NewAlbumRepository(db *DB) domain.AlbumRepository {
	return &albumRepository{db: db}
}

func (r *albumRepository) GetByID(ctx context.Context, id string) (*domain.AlbumDTO, error) {
	query := fmt.Sprintf(`SELECT %s FROM albums a WHERE a.id = ?`, albumSelectFields)
	var album domain.Album
	err := r.db.GetContext(ctx, &album, query, id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get album by id: %w", err)
	}

	dto := &domain.AlbumDTO{Album: album}

	// Fetch artists
	var artists []*domain.Artist
	artistQuery := `
		SELECT art.* FROM artists art
		JOIN album_artists aa ON art.id = aa.artist_id
		WHERE aa.album_id = ?
		ORDER BY aa.position
	`
	err = r.db.SelectContext(ctx, &artists, artistQuery, id)
	if err != nil {
		return nil, fmt.Errorf("failed to get album artists: %w", err)
	}
	dto.Artists = artists

	return dto, nil
}

func (r *albumRepository) GetByNormalizationKey(ctx context.Context, key string) (*domain.Album, error) {
	var album domain.Album
	err := r.db.GetContext(ctx, &album, "SELECT * FROM albums WHERE normalization_key = ?", key)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get album by normalization key: %w", err)
	}
	return &album, nil
}

type albumRow struct {
	domain.Album
	ArtistNames sql.NullString `db:"artist_names"`
	ArtistIDs   sql.NullString `db:"artist_ids"`
}

func (r *albumRepository) GetByArtistID(ctx context.Context, artistID string) ([]*domain.AlbumDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, 
		  COALESCE(
		    GROUP_CONCAT(DISTINCT aa_art.name), 
		    GROUP_CONCAT(DISTINCT t_art.name)
		  ) AS artist_names,
		  COALESCE(
		    GROUP_CONCAT(DISTINCT aa_art.id), 
		    GROUP_CONCAT(DISTINCT t_art.id)
		  ) AS artist_ids
		FROM albums a
		LEFT JOIN album_artists aa ON a.id = aa.album_id
		LEFT JOIN artists aa_art ON aa.artist_id = aa_art.id
		LEFT JOIN tracks t ON a.id = t.album_id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists t_art ON ta.artist_id = t_art.id
		WHERE a.id IN (SELECT album_id FROM album_artists WHERE artist_id = ?)
		   OR a.id IN (SELECT DISTINCT album_id FROM tracks t JOIN track_artists ta ON t.id = ta.track_id WHERE ta.artist_id = ?)
		   OR a.id IN (SELECT DISTINCT album_id FROM tracks t JOIN track_album_artists taa ON t.id = taa.track_id WHERE taa.artist_id = ?)
		GROUP BY a.id
		ORDER BY a.year DESC, a.sort_title
	`, albumSelectFields)
	var rows []albumRow
	err := r.db.SelectContext(ctx, &rows, query, artistID, artistID, artistID)
	if err != nil {
		return nil, fmt.Errorf("failed to get albums by artist id: %w", err)
	}
	return r.scanAlbumRows(rows), nil
}

func (r *albumRepository) GetRecentlyAdded(ctx context.Context, limit int) ([]*domain.AlbumDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, 
		  COALESCE(
		    GROUP_CONCAT(DISTINCT aa_art.name), 
		    GROUP_CONCAT(DISTINCT t_art.name)
		  ) AS artist_names,
		  COALESCE(
		    GROUP_CONCAT(DISTINCT aa_art.id), 
		    GROUP_CONCAT(DISTINCT t_art.id)
		  ) AS artist_ids
		FROM albums a
		LEFT JOIN album_artists aa ON a.id = aa.album_id
		LEFT JOIN artists aa_art ON aa.artist_id = aa_art.id
		LEFT JOIN tracks t ON a.id = t.album_id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists t_art ON ta.artist_id = t_art.id
		GROUP BY a.id
		ORDER BY a.created_at DESC
		LIMIT ?
	`, albumSelectFields)
	var rows []albumRow
	err := r.db.SelectContext(ctx, &rows, query, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to get recently added albums: %w", err)
	}
	return r.scanAlbumRows(rows), nil
}

func (r *albumRepository) scanAlbumRows(rows []albumRow) []*domain.AlbumDTO {
	dtos := make([]*domain.AlbumDTO, len(rows))
	for i, row := range rows {
		dtos[i] = &domain.AlbumDTO{Album: row.Album}
		if row.ArtistNames.Valid && row.ArtistIDs.Valid {
			names := strings.Split(row.ArtistNames.String, ",")
			ids := strings.Split(row.ArtistIDs.String, ",")
			for j, name := range names {
				name = strings.TrimSpace(name)
				if name != "" {
					id := ""
					if j < len(ids) {
						id = strings.TrimSpace(ids[j])
					}
					dtos[i].Artists = append(dtos[i].Artists, &domain.Artist{ID: id, Name: name})
				}
			}
		}
	}
	return dtos
}

func (r *albumRepository) GetAll(ctx context.Context) ([]*domain.AlbumDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, 
		  COALESCE(
		    GROUP_CONCAT(DISTINCT aa_art.name), 
		    GROUP_CONCAT(DISTINCT t_art.name)
		  ) AS artist_names,
		  COALESCE(
		    GROUP_CONCAT(DISTINCT aa_art.id), 
		    GROUP_CONCAT(DISTINCT t_art.id)
		  ) AS artist_ids
		FROM albums a
		LEFT JOIN album_artists aa ON a.id = aa.album_id
		LEFT JOIN artists aa_art ON aa.artist_id = aa_art.id
		LEFT JOIN tracks t ON a.id = t.album_id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists t_art ON ta.artist_id = t_art.id
		GROUP BY a.id
		ORDER BY a.sort_title
	`, albumSelectFields)
	var rows []albumRow
	err := r.db.SelectContext(ctx, &rows, query)
	if err != nil {
		return nil, fmt.Errorf("failed to get all albums: %w", err)
	}
	return r.scanAlbumRows(rows), nil
}

func (r *albumRepository) Save(ctx context.Context, album *domain.Album) error {
	now := time.Now()
	if album.CreatedAt.IsZero() {
		album.CreatedAt = now
	}
	album.UpdatedAt = now

	query := `
		INSERT INTO albums (
			id, title, sort_title, normalization_key, year, copyright, artwork_key, created_at, updated_at
		) VALUES (
			:id, :title, :sort_title, :normalization_key, :year, :copyright, :artwork_key, :created_at, :updated_at
		)`

	_, err := r.db.NamedExecContext(ctx, query, album)
	if err != nil {
		return fmt.Errorf("failed to save album: %w", err)
	}
	return nil
}

func (r *albumRepository) Upsert(ctx context.Context, album *domain.Album) error {
	now := time.Now()
	if album.CreatedAt.IsZero() {
		album.CreatedAt = now
	}
	album.UpdatedAt = now

	query := `
		INSERT INTO albums (
			id, title, sort_title, normalization_key, year, copyright, artwork_key, created_at, updated_at
		) VALUES (
			:id, :title, :sort_title, :normalization_key, :year, :copyright, :artwork_key, :created_at, :updated_at
		) ON CONFLICT(id) DO UPDATE SET
			title = excluded.title,
			sort_title = excluded.sort_title,
			normalization_key = excluded.normalization_key,
			year = excluded.year,
			copyright = excluded.copyright,
			artwork_key = excluded.artwork_key,
			updated_at = excluded.updated_at
	`

	_, err := r.db.NamedExecContext(ctx, query, album)
	if err != nil {
		return fmt.Errorf("failed to upsert album: %w", err)
	}
	return nil
}

func (r *albumRepository) SetArtists(ctx context.Context, albumID string, artistIDs []string) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	_, err = tx.ExecContext(ctx, "DELETE FROM album_artists WHERE album_id = ?", albumID)
	if err != nil {
		return err
	}

	for i, artistID := range artistIDs {
		_, err = tx.ExecContext(ctx, "INSERT INTO album_artists (album_id, artist_id, position) VALUES (?, ?, ?)", albumID, artistID, i)
		if err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (r *albumRepository) DeleteOrphaned(ctx context.Context) error {
	query := `DELETE FROM albums WHERE id NOT IN (SELECT DISTINCT album_id FROM tracks WHERE album_id IS NOT NULL)`
	_, err := r.db.ExecContext(ctx, query)
	if err != nil {
		return fmt.Errorf("failed to delete orphaned albums: %w", err)
	}
	return nil
}
