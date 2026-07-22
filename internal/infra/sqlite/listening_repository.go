package sqlite

import (
	"context"
	"fmt"
	"strings"
	"time"

	"airmedy/internal/domain"
	"github.com/google/uuid"
)

type listeningRepository struct{ db *DB }

func NewListeningRepository(db *DB) domain.ListeningRepository { return &listeningRepository{db: db} }

func (r *listeningRepository) RecordSession(ctx context.Context, s domain.ListeningSession) error {
	if s.TrackID == "" || s.ListenedSeconds <= 0 {
		return nil
	}
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin listening session transaction: %w", err)
	}
	defer tx.Rollback()
	if _, err = tx.ExecContext(ctx, `INSERT INTO listening_sessions (id, track_id, started_at, ended_at, listened_seconds, qualified_play) VALUES (?, ?, ?, ?, ?, ?)`, uuid.NewString(), s.TrackID, s.StartedAt, s.EndedAt, s.ListenedSeconds, s.QualifiedPlay); err != nil {
		return fmt.Errorf("insert listening session: %w", err)
	}
	play := 0
	if s.QualifiedPlay {
		play = 1
	}
	for date, seconds := range splitListeningByLocalDate(s.StartedAt, s.EndedAt, s.ListenedSeconds) {
		datePlay := 0
		if date == s.EndedAt.In(time.Local).Format("2006-01-02") {
			datePlay = play
		}
		if _, err = tx.ExecContext(ctx, `INSERT INTO daily_track_listening_stats (local_date, track_id, listened_seconds, play_count) VALUES (?, ?, ?, ?)
            ON CONFLICT(local_date, track_id) DO UPDATE SET listened_seconds = listened_seconds + excluded.listened_seconds, play_count = play_count + excluded.play_count`, date, s.TrackID, seconds, datePlay); err != nil {
			return fmt.Errorf("upsert daily listening stats: %w", err)
		}
	}
	if err = tx.Commit(); err != nil {
		return fmt.Errorf("commit listening session: %w", err)
	}
	return nil
}

func splitListeningByLocalDate(start, end time.Time, seconds int) map[string]int {
	result := make(map[string]int)
	if end.Before(start) || end.Equal(start) {
		result[start.In(time.Local).Format("2006-01-02")] = seconds
		return result
	}
	wallSeconds := end.Sub(start).Seconds()
	remaining := seconds
	for cursor := start.In(time.Local); cursor.Before(end.In(time.Local)); {
		year, month, day := cursor.Date()
		next := time.Date(year, month, day+1, 0, 0, 0, 0, time.Local)
		if next.After(end.In(time.Local)) {
			next = end.In(time.Local)
		}
		part := int((next.Sub(cursor).Seconds() / wallSeconds) * float64(seconds))
		if next.Equal(end.In(time.Local)) {
			part = remaining
		}
		if part > 0 {
			result[cursor.Format("2006-01-02")] += part
			remaining -= part
		}
		cursor = next
	}
	if len(result) == 0 {
		result[start.In(time.Local).Format("2006-01-02")] = seconds
	}
	return result
}

func (r *listeningRepository) CleanupSessions(ctx context.Context, before time.Time) error {
	if _, err := r.db.ExecContext(ctx, `DELETE FROM listening_sessions WHERE ended_at < ?`, before); err != nil {
		return fmt.Errorf("cleanup listening sessions: %w", err)
	}
	return nil
}

func periodStart(period domain.ListeningRange, now time.Time) (time.Time, bool, error) {
	local := now.In(time.Local)
	year, month, day := local.Date()
	switch period {
	case domain.ListeningRange7D:
		return time.Date(year, month, day-6, 0, 0, 0, 0, time.Local), true, nil
	case domain.ListeningRange30D:
		return time.Date(year, month, day-29, 0, 0, 0, 0, time.Local), true, nil
	case domain.ListeningRangeAll:
		return time.Time{}, false, nil
	default:
		return time.Time{}, false, fmt.Errorf("invalid listening range %q", period)
	}
}

func (r *listeningRepository) GetInsights(ctx context.Context, period domain.ListeningRange, now time.Time) (*domain.AnalyticsInsights, error) {
	start, bounded, err := periodStart(period, now.In(time.Local))
	if err != nil {
		return nil, err
	}
	result := &domain.AnalyticsInsights{Activity: []domain.AnalyticsPoint{}, Quality: []domain.AnalyticsQualityBucket{}, Genres: []domain.AnalyticsGenre{}, TopArtists: []domain.AnalyticsArtist{}, TopTracks: []domain.AnalyticsTrack{}}
	where, args := "", []any{}
	if bounded {
		where, args = "WHERE local_date >= ?", []any{start.Format("2006-01-02")}
	}
	if err := r.db.GetContext(ctx, &result.ListenedSeconds, `SELECT COALESCE(SUM(listened_seconds), 0) FROM daily_track_listening_stats `+where, args...); err != nil {
		return nil, fmt.Errorf("sum listening time: %w", err)
	}
	if err := r.db.GetContext(ctx, &result.Plays, `SELECT COALESCE(SUM(play_count), 0) FROM daily_track_listening_stats `+where, args...); err != nil {
		return nil, fmt.Errorf("sum listening plays: %w", err)
	}
	if bounded {
		windowDays := 7
		if period == domain.ListeningRange30D {
			windowDays = 30
		}
		previousStart := start.AddDate(0, 0, -windowDays)
		var previous int
		if err := r.db.GetContext(ctx, &previous, `SELECT COALESCE(SUM(listened_seconds), 0) FROM daily_track_listening_stats WHERE local_date >= ? AND local_date < ?`, previousStart.Format("2006-01-02"), start.Format("2006-01-02")); err != nil {
			return nil, fmt.Errorf("sum previous listening time: %w", err)
		}
		if previous > 0 {
			v := (float64(result.ListenedSeconds-previous) / float64(previous)) * 100
			result.ChangePercent = &v
		}
	}
	if err := r.db.QueryRowxContext(ctx, `SELECT
		COUNT(*),
		COALESCE((SELECT COUNT(*) FROM albums), 0),
		COALESCE((SELECT COUNT(*) FROM artists), 0),
		COALESCE((SELECT COUNT(*) FROM playlists WHERE id != 'favorites'), 0),
		COALESCE(SUM(file_size), 0)
		FROM tracks`).Scan(&result.LibraryTracks, &result.LibraryAlbums, &result.LibraryArtists, &result.LibraryPlaylists, &result.LibraryBytes); err != nil {
		return nil, fmt.Errorf("read library summary: %w", err)
	}

	activitySQL := `SELECT local_date AS date, SUM(listened_seconds) AS listened_seconds FROM daily_track_listening_stats ` + where + ` GROUP BY local_date ORDER BY local_date`
	if period == domain.ListeningRangeAll {
		activitySQL = `SELECT substr(local_date, 1, 7) AS date, SUM(listened_seconds) AS listened_seconds FROM daily_track_listening_stats GROUP BY substr(local_date, 1, 7) ORDER BY date`
		args = nil
	}
	if err := r.db.SelectContext(ctx, &result.Activity, activitySQL, args...); err != nil {
		return nil, fmt.Errorf("read listening activity: %w", err)
	}
	if bounded {
		byDate := make(map[string]int, len(result.Activity))
		for _, point := range result.Activity {
			byDate[point.Date] = point.ListenedSeconds
		}
		filled := make([]domain.AnalyticsPoint, 0, int(now.Sub(start).Hours()/24)+1)
		for day := start; !day.After(now); day = day.AddDate(0, 0, 1) {
			date := day.Format("2006-01-02")
			filled = append(filled, domain.AnalyticsPoint{Date: date, ListenedSeconds: byDate[date]})
		}
		result.Activity = filled
	}

	genreRows := []domain.AnalyticsGenre{}
	genreSQL := `SELECT g.name, SUM(d.listened_seconds) listened_seconds FROM daily_track_listening_stats d JOIN track_genres tg ON tg.track_id=d.track_id JOIN genres g ON g.id=tg.genre_id ` + strings.Replace(where, "local_date", "d.local_date", 1) + ` GROUP BY g.id ORDER BY listened_seconds DESC, g.name`
	if err := r.db.SelectContext(ctx, &genreRows, genreSQL, args...); err != nil {
		return nil, fmt.Errorf("read listening genres: %w", err)
	}
	otherSeconds := 0
	for index, genre := range genreRows {
		if index < 5 {
			result.Genres = append(result.Genres, genre)
			continue
		}
		otherSeconds += genre.ListenedSeconds
	}
	if otherSeconds > 0 {
		result.Genres = append(result.Genres, domain.AnalyticsGenre{ListenedSeconds: otherSeconds, IsOther: true})
	}

	qualityRows := []struct {
		Format     string `db:"format"`
		Codec      string `db:"codec"`
		BitDepth   int    `db:"bit_depth"`
		SampleRate int    `db:"sample_rate"`
		Count      int    `db:"count"`
	}{}
	if err := r.db.SelectContext(ctx, &qualityRows, `SELECT lower(format) format, lower(codec) codec, bit_depth, sample_rate, COUNT(*) count FROM tracks GROUP BY lower(format), lower(codec), bit_depth, sample_rate`); err != nil {
		return nil, fmt.Errorf("read audio quality: %w", err)
	}
	quality := map[string]int{"lossy": 0, "lossless": 0, "hi_res": 0, "dsd": 0, "unknown": 0}
	for _, row := range qualityRows {
		quality[classifyQuality(row.Format, row.Codec, row.BitDepth, row.SampleRate)] += row.Count
	}
	for _, kind := range []string{"lossy", "lossless", "hi_res", "dsd", "unknown"} {
		if quality[kind] > 0 {
			result.Quality = append(result.Quality, domain.AnalyticsQualityBucket{Kind: kind, Count: quality[kind]})
		}
	}

	artistSQL := `SELECT a.id, a.name, COALESCE(a.artwork_key_manual, a.artwork_key_local, a.artwork_key_online, '') artwork_key, SUM(d.listened_seconds) listened_seconds FROM daily_track_listening_stats d JOIN track_artists ta ON ta.track_id=d.track_id JOIN artists a ON a.id=ta.artist_id ` + strings.Replace(where, "local_date", "d.local_date", 1) + ` GROUP BY a.id ORDER BY listened_seconds DESC, a.name LIMIT 25`
	if err := r.db.SelectContext(ctx, &result.TopArtists, artistSQL, args...); err != nil {
		return nil, fmt.Errorf("read top artists: %w", err)
	}
	trackSQL := `SELECT t.id, t.title,
		COALESCE((SELECT GROUP_CONCAT(name, ', ') FROM (SELECT a.name FROM track_artists ta JOIN artists a ON a.id=ta.artist_id WHERE ta.track_id=t.id ORDER BY ta.position)), '') artist,
		stats.play_count, stats.listened_seconds
		FROM (SELECT d.track_id, SUM(d.play_count) play_count, SUM(d.listened_seconds) listened_seconds FROM daily_track_listening_stats d ` + strings.Replace(where, "local_date", "d.local_date", 1) + ` GROUP BY d.track_id) stats
		JOIN tracks t ON t.id=stats.track_id ORDER BY stats.play_count DESC, t.title LIMIT 25`
	if err := r.db.SelectContext(ctx, &result.TopTracks, trackSQL, args...); err != nil {
		return nil, fmt.Errorf("read top tracks: %w", err)
	}
	return result, nil
}

func classifyQuality(format, codec string, bitDepth, sampleRate int) string {
	if format == "dsf" || format == "dff" {
		return "dsd"
	}
	lossy := map[string]bool{"mp3": true, "aac": true, "ogg": true, "opus": true}
	if lossy[format] {
		return "lossy"
	}
	if format == "m4a" || format == "mp4" {
		if codec == "" {
			return "unknown"
		}
		if codec != "alac" {
			return "lossy"
		}
	}
	lossless := map[string]bool{"flac": true, "wav": true, "aiff": true, "ape": true, "wv": true, "m4a": true, "mp4": true}
	if !lossless[format] {
		return "unknown"
	}
	if bitDepth > 16 || sampleRate > 48000 {
		return "hi_res"
	}
	return "lossless"
}
