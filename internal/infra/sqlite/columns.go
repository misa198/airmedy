package sqlite

const trackSelectFields = `
	t.id, t.path, t.title, t.sort_title, 
	COALESCE(t.album_id, '') AS album_id, 
	COALESCE(t.year, 0) AS year, 
	COALESCE(t.track_number, 0) AS track_number, 
	COALESCE(t.total_tracks, 0) AS total_tracks, 
	COALESCE(t.disc_number, 0) AS disc_number, 
	COALESCE(t.total_discs, 0) AS total_discs, 
	COALESCE(t.duration, 0) AS duration, 
	COALESCE(t.bitrate, 0) AS bitrate, 
	COALESCE(t.sample_rate, 0) AS sample_rate, 
	COALESCE(t.format, '') AS format, 
	COALESCE(t.artwork_key, '') AS artwork_key, 
	COALESCE(t.raw_artist_names, '') AS raw_artist_names,
	COALESCE(t.raw_album_artist_names, '') AS raw_album_artist_names,
	COALESCE(t.raw_genre_names, '') AS raw_genre_names,
	COALESCE(t.raw_composer_names, '') AS raw_composer_names,
	COALESCE(t.copyright, '') AS copyright,
	COALESCE(t.bpm, 0) AS bpm,
	COALESCE(t.label, '') AS label,
	COALESCE(t.isrc, '') AS isrc,
	COALESCE(t.play_count, 0) AS play_count,
	COALESCE(t.other_metadata, '{}') AS other_metadata,
	COALESCE(t.file_size, 0) AS file_size,
	COALESCE(t.is_favorite, 0) AS is_favorite,
	t.mtime,
	t.created_at, t.updated_at
`

const albumSelectFields = `
	a.id, a.title, a.sort_title, 
	COALESCE(a.normalization_key, '') AS normalization_key,
	COALESCE(a.year, 0) AS year, 
	COALESCE(a.copyright, '') AS copyright,
	COALESCE(a.artwork_key, '') AS artwork_key, 
	a.created_at, a.updated_at
`

const playlistSelectFields = `
	id, name, COALESCE(description, '') AS description, artwork_key, created_at, updated_at
`

const lyricSelectFields = `
	track_id, content, COALESCE(source, '') AS source,
	COALESCE(meta_content, '') AS meta_content,
	COALESCE(meta_source, '') AS meta_source,
	created_at, updated_at
`
