package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"airmedy/internal/domain"
)

type trackRepository struct {
	db *DB
}

func NewTrackRepository(db *DB) domain.TrackRepository {
	return &trackRepository{db: db}
}

func (r *trackRepository) GetByID(ctx context.Context, id string) (*domain.TrackDTO, error) {
	query := fmt.Sprintf(`SELECT %s FROM tracks t WHERE t.id = ?`, trackSelectFields)
	var track domain.Track
	err := r.db.GetContext(ctx, &track, query, id)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get track by id: %w", err)
	}

	dto := &domain.TrackDTO{Track: track}
	if err := r.populateRelationships(ctx, dto); err != nil {
		return nil, err
	}

	return dto, nil
}

func (r *trackRepository) populateRelationships(ctx context.Context, dto *domain.TrackDTO) error {
	// Album
	if dto.AlbumID != "" {
		var album domain.Album
		err := r.db.GetContext(ctx, &album, "SELECT * FROM albums WHERE id = ?", dto.AlbumID)
		if err == nil {
			dto.Album = &album
		}
	}

	// Artists
	var artists []*domain.Artist
	err := r.db.SelectContext(ctx, &artists, `
		SELECT art.* FROM artists art
		JOIN track_artists ta ON art.id = ta.artist_id
		WHERE ta.track_id = ?
		ORDER BY ta.position
	`, dto.ID)
	if err == nil {
		dto.Artists = artists
	}

	// Album Artists
	var albumArtists []*domain.Artist
	err = r.db.SelectContext(ctx, &albumArtists, `
		SELECT art.* FROM artists art
		JOIN track_album_artists taa ON art.id = taa.artist_id
		WHERE taa.track_id = ?
		ORDER BY taa.position
	`, dto.ID)
	if err == nil {
		dto.AlbumArtists = albumArtists
	}

	// Genres
	var genres []*domain.Genre
	err = r.db.SelectContext(ctx, &genres, `
		SELECT g.* FROM genres g
		JOIN track_genres tg ON g.id = tg.genre_id
		WHERE tg.track_id = ?
		ORDER BY tg.position
	`, dto.ID)
	if err == nil {
		dto.Genres = genres
	}

	// Composers
	var composers []*domain.Composer
	err = r.db.SelectContext(ctx, &composers, `
		SELECT c.* FROM composers c
		JOIN track_composers tc ON c.id = tc.composer_id
		WHERE tc.track_id = ?
		ORDER BY tc.position
	`, dto.ID)
	if err == nil {
		dto.Composers = composers
	}

	return nil
}

func (r *trackRepository) GetByPath(ctx context.Context, path string) (*domain.TrackDTO, error) {
	query := fmt.Sprintf(`SELECT %s FROM tracks t WHERE t.path = ?`, trackSelectFields)
	var track domain.Track
	err := r.db.GetContext(ctx, &track, query, path)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to get track by path: %w", err)
	}

	dto := &domain.TrackDTO{Track: track}
	if err := r.populateRelationships(ctx, dto); err != nil {
		return nil, err
	}

	return dto, nil
}

type trackRow struct {
	domain.Track
	AlbumTitle      sql.NullString `db:"album_title"`
	AlbumArtworkKey sql.NullString `db:"album_artwork_key"`
	AlbumYear       sql.NullInt64  `db:"album_year"`
	ArtistNames     sql.NullString `db:"artist_names"`
	ArtistIDs       sql.NullString `db:"artist_ids"`
}

func (r *trackRepository) GetByAlbumID(ctx context.Context, albumID string) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.album_id = ?
		GROUP BY t.id
		ORDER BY t.disc_number, t.track_number, t.sort_title
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, albumID)
	if err != nil {
		return nil, fmt.Errorf("failed to get tracks by album id: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) GetByArtistID(ctx context.Context, artistID string) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.id IN (SELECT track_id FROM track_artists WHERE artist_id = ?)
		   OR t.id IN (SELECT track_id FROM track_album_artists WHERE artist_id = ?)
		   OR t.album_id IN (SELECT album_id FROM album_artists WHERE artist_id = ?)
		GROUP BY t.id
		ORDER BY t.year DESC, a.title, t.disc_number, t.track_number, t.sort_title
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, artistID, artistID, artistID)
	if err != nil {
		return nil, fmt.Errorf("failed to get tracks by artist id: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) GetByGenreID(ctx context.Context, genreID string) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.id IN (SELECT track_id FROM track_genres WHERE genre_id = ?)
		GROUP BY t.id
		ORDER BY t.year DESC, a.title, t.disc_number, t.track_number, t.sort_title
		`, trackSelectFields)
		var rows []trackRow
		err := r.db.SelectContext(ctx, &rows, query, genreID)
		if err != nil {
		return nil, fmt.Errorf("failed to get tracks by genre id: %w", err)
		}
		return r.scanTrackRows(rows), nil
		}

		func (r *trackRepository) GetByComposerID(ctx context.Context, composerID string) ([]*domain.TrackDTO, error) {
		query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year,
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.id IN (SELECT track_id FROM track_composers WHERE composer_id = ?)
		GROUP BY t.id
		ORDER BY t.year DESC, a.title, t.disc_number, t.track_number, t.sort_title
		`, trackSelectFields)

	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, composerID)
	if err != nil {
		return nil, fmt.Errorf("failed to get tracks by composer id: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) scanTrackRows(rows []trackRow) []*domain.TrackDTO {
	dtos := make([]*domain.TrackDTO, len(rows))
	for i, row := range rows {
		dtos[i] = &domain.TrackDTO{Track: row.Track}
		if row.AlbumTitle.Valid {
			dtos[i].Album = &domain.Album{
				ID:         row.AlbumID,
				Title:      row.AlbumTitle.String,
				ArtworkKey: row.AlbumArtworkKey.String,
				Year:       int(row.AlbumYear.Int64),
			}
		}
		if row.ArtistNames.Valid && row.ArtistIDs.Valid {
			names := strings.Split(row.ArtistNames.String, "; ")
			ids := strings.Split(row.ArtistIDs.String, "; ")
			for j, name := range names {
				id := ""
				if j < len(ids) {
					id = ids[j]
				}
				dtos[i].Artists = append(dtos[i].Artists, &domain.Artist{ID: id, Name: name})
			}
		}
	}
	return dtos
}

func (r *trackRepository) GetByPathPrefix(ctx context.Context, prefix string) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.path LIKE ? || '%%'
		GROUP BY t.id
		ORDER BY t.sort_title
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, prefix)
	if err != nil {
		return nil, fmt.Errorf("failed to get tracks by path prefix: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) Count(ctx context.Context) (int, error) {
	var count int
	err := r.db.GetContext(ctx, &count, "SELECT COUNT(*) FROM tracks")
	if err != nil {
		return 0, fmt.Errorf("failed to count tracks: %w", err)
	}
	return count, nil
}

func (r *trackRepository) GetPaginated(ctx context.Context, offset, limit int) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year,
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		GROUP BY t.id
		ORDER BY t.sort_title
		LIMIT ? OFFSET ?
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, limit, offset)
	if err != nil {
		return nil, fmt.Errorf("failed to get paginated tracks: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) GetByIDs(ctx context.Context, ids []string) ([]*domain.TrackDTO, error) {
	if len(ids) == 0 {
		return nil, nil
	}
	placeholders := strings.Repeat("?,", len(ids))
	placeholders = placeholders[:len(placeholders)-1]
	args := make([]interface{}, len(ids))
	for i, id := range ids {
		args[i] = id
	}
	q := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year,
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.id IN (%s)
		GROUP BY t.id
	`, trackSelectFields, placeholders)
	var rows []trackRow
	if err := r.db.SelectContext(ctx, &rows, q, args...); err != nil {
		return nil, fmt.Errorf("failed to get tracks by ids: %w", err)
	}
	// Reorder to match input order (IN clause doesn't preserve order)
	fetched := r.scanTrackRows(rows)
	byID := make(map[string]*domain.TrackDTO, len(fetched))
	for _, t := range fetched {
		byID[t.ID] = t
	}
	ordered := make([]*domain.TrackDTO, 0, len(ids))
	for _, id := range ids {
		if t, ok := byID[id]; ok {
			ordered = append(ordered, t)
		}
	}
	return ordered, nil
}

func (r *trackRepository) GetAll(ctx context.Context) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year,
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		GROUP BY t.id
		ORDER BY t.sort_title
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query)
	if err != nil {
		return nil, fmt.Errorf("failed to get all tracks: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) GetFavorites(ctx context.Context) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year,
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.is_favorite = 1
		GROUP BY t.id
		ORDER BY t.sort_title
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query)
	if err != nil {
		return nil, fmt.Errorf("failed to get favorite tracks: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) ToggleFavorite(ctx context.Context, id string) (bool, error) {
	_, err := r.db.ExecContext(ctx,
		"UPDATE tracks SET is_favorite = NOT is_favorite, updated_at = ? WHERE id = ?",
		time.Now(), id,
	)
	if err != nil {
		return false, fmt.Errorf("failed to toggle favorite: %w", err)
	}
	var val bool
	err = r.db.GetContext(ctx, &val, "SELECT is_favorite FROM tracks WHERE id = ?", id)
	if err != nil {
		return false, fmt.Errorf("failed to read favorite state: %w", err)
	}
	return val, nil
}

func (r *trackRepository) IncrementPlayCount(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx,
		"UPDATE tracks SET play_count = play_count + 1, updated_at = ? WHERE id = ?",
		time.Now(), id,
	)
	if err != nil {
		return fmt.Errorf("failed to increment play count: %w", err)
	}
	return nil
}

func (r *trackRepository) GetMostListened(ctx context.Context, limit int) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.play_count > 0
		GROUP BY t.id
		ORDER BY t.play_count DESC
		LIMIT ?
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to get most listened tracks: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) GetLeastListened(ctx context.Context, limit int) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		GROUP BY t.id
		ORDER BY t.play_count ASC, t.updated_at ASC
		LIMIT ?
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to get least listened tracks: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

func (r *trackRepository) GetRecentlyPlayed(ctx context.Context, limit int) ([]*domain.TrackDTO, error) {
	query := fmt.Sprintf(`
		SELECT %s, a.title AS album_title, a.artwork_key AS album_artwork_key, a.year AS album_year, 
		       GROUP_CONCAT(art.name, '; ') AS artist_names,
		       GROUP_CONCAT(art.id, '; ') AS artist_ids
		FROM tracks t
		LEFT JOIN albums a ON t.album_id = a.id
		LEFT JOIN track_artists ta ON t.id = ta.track_id
		LEFT JOIN artists art ON ta.artist_id = art.id
		WHERE t.play_count > 0
		GROUP BY t.id
		ORDER BY t.updated_at DESC
		LIMIT ?
	`, trackSelectFields)
	var rows []trackRow
	err := r.db.SelectContext(ctx, &rows, query, limit)
	if err != nil {
		return nil, fmt.Errorf("failed to get recently played tracks: %w", err)
	}
	return r.scanTrackRows(rows), nil
}

type trackDB struct {
	domain.Track
	AlbumID sql.NullString `db:"album_id"`
}

func (r *trackRepository) Save(ctx context.Context, track *domain.Track) error {
	now := time.Now()
	if track.CreatedAt.IsZero() {
		track.CreatedAt = now
	}
	track.UpdatedAt = now

	dbTrack := trackDB{
		Track:   *track,
		AlbumID: toNullString(track.AlbumID),
	}

	query := `
		INSERT INTO tracks (
			id, path, title, sort_title,
			album_id, year, track_number, total_tracks, disc_number, total_discs,
			duration, bitrate, sample_rate, format, artwork_key,
			raw_artist_names, raw_album_artist_names, raw_genre_names, raw_composer_names,
			copyright, bpm, label, isrc, play_count, other_metadata, file_size, is_favorite, mtime, created_at, updated_at
		) VALUES (
			:id, :path, :title, :sort_title,
			:album_id, :year, :track_number, :total_tracks, :disc_number, :total_discs,
			:duration, :bitrate, :sample_rate, :format, :artwork_key,
			:raw_artist_names, :raw_album_artist_names, :raw_genre_names, :raw_composer_names,
			:copyright, :bpm, :label, :isrc, :play_count, :other_metadata, :file_size, :is_favorite, :mtime, :created_at, :updated_at
		)`

	_, err := r.db.NamedExecContext(ctx, query, dbTrack)
	if err != nil {
		return fmt.Errorf("failed to save track: %w", err)
	}
	return nil
}

func (r *trackRepository) Upsert(ctx context.Context, track *domain.Track) error {
	now := time.Now()
	if track.CreatedAt.IsZero() {
		track.CreatedAt = now
	}
	track.UpdatedAt = now

	dbTrack := trackDB{
		Track:   *track,
		AlbumID: toNullString(track.AlbumID),
	}

	query := `
		INSERT INTO tracks (
			id, path, title, sort_title,
			album_id, year, track_number, total_tracks, disc_number, total_discs,
			duration, bitrate, sample_rate, format, artwork_key,
			raw_artist_names, raw_album_artist_names, raw_genre_names, raw_composer_names,
			copyright, bpm, label, isrc, play_count, other_metadata, file_size, is_favorite, mtime, created_at, updated_at
		) VALUES (
			:id, :path, :title, :sort_title,
			:album_id, :year, :track_number, :total_tracks, :disc_number, :total_discs,
			:duration, :bitrate, :sample_rate, :format, :artwork_key,
			:raw_artist_names, :raw_album_artist_names, :raw_genre_names, :raw_composer_names,
			:copyright, :bpm, :label, :isrc, :play_count, :other_metadata, :file_size, :is_favorite, :mtime, :created_at, :updated_at
		) ON CONFLICT(path) DO UPDATE SET
			title = excluded.title,
			sort_title = excluded.sort_title,
			album_id = excluded.album_id,
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
			raw_artist_names = excluded.raw_artist_names,
			raw_album_artist_names = excluded.raw_album_artist_names,
			raw_genre_names = excluded.raw_genre_names,
			raw_composer_names = excluded.raw_composer_names,
			copyright = excluded.copyright,
			bpm = excluded.bpm,
			label = excluded.label,
			isrc = excluded.isrc,
			other_metadata = excluded.other_metadata,
			file_size = excluded.file_size,
			mtime = excluded.mtime,
			updated_at = excluded.updated_at
	`

	_, err := r.db.NamedExecContext(ctx, query, dbTrack)
	if err != nil {
		return fmt.Errorf("failed to upsert track: %w", err)
	}
	return nil
}

func (r *trackRepository) SetArtists(ctx context.Context, trackID string, artistIDs []string) error {
	return r.setJunction(ctx, "track_artists", "track_id", "artist_id", trackID, artistIDs)
}

func (r *trackRepository) SetAlbumArtists(ctx context.Context, trackID string, artistIDs []string) error {
	return r.setJunction(ctx, "track_album_artists", "track_id", "artist_id", trackID, artistIDs)
}

func (r *trackRepository) SetGenres(ctx context.Context, trackID string, genreIDs []string) error {
	return r.setJunction(ctx, "track_genres", "track_id", "genre_id", trackID, genreIDs)
}

func (r *trackRepository) SetComposers(ctx context.Context, trackID string, composerIDs []string) error {
	return r.setJunction(ctx, "track_composers", "track_id", "composer_id", trackID, composerIDs)
}

func (r *trackRepository) setJunction(ctx context.Context, table, idCol, valCol, id string, vals []string) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	_, err = tx.ExecContext(ctx, fmt.Sprintf("DELETE FROM %s WHERE %s = ?", table, idCol), id)
	if err != nil {
		return err
	}

	for i, val := range vals {
		_, err = tx.ExecContext(ctx, fmt.Sprintf("INSERT INTO %s (%s, %s, position) VALUES (?, ?, ?)", table, idCol, valCol), id, val, i)
		if err != nil {
			return err
		}
	}

	return tx.Commit()
}

func toNullString(s string) sql.NullString {
	if s == "" {
		return sql.NullString{Valid: false}
	}
	return sql.NullString{String: s, Valid: true}
}

func (r *trackRepository) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM tracks WHERE id = ?", id)
	if err != nil {
		return fmt.Errorf("failed to delete track: %w", err)
	}
	return nil
}

func (r *trackRepository) DeleteByPathPrefix(ctx context.Context, prefix string) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM tracks WHERE path LIKE ? || '%'", prefix)
	if err != nil {
		return fmt.Errorf("failed to delete tracks by path prefix: %w", err)
	}
	return nil
}

func (r *trackRepository) GetAllArtworkKeys(ctx context.Context) ([]string, error) {
	// Artworks can be in tracks or albums (though they should be consistent)
	query := `
		SELECT DISTINCT artwork_key FROM tracks WHERE artwork_key IS NOT NULL AND artwork_key != ''
		UNION
		SELECT DISTINCT artwork_key FROM albums WHERE artwork_key IS NOT NULL AND artwork_key != ''
	`
	var keys []string
	err := r.db.SelectContext(ctx, &keys, query)
	if err != nil {
		return nil, fmt.Errorf("failed to get all artwork keys: %w", err)
	}
	return keys, nil
}
