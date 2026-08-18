package sqlite

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"sync"
	"time"

	"airmedy/internal/domain"
	"github.com/google/uuid"
)

const listeningInsightsTopItemsLimit = 50

type listeningRepository struct {
	db         *DB
	sourceOnce sync.Once
}

func NewListeningRepository(db *DB) domain.ListeningRepository { return &listeningRepository{db: db} }

func (r *listeningRepository) sourceID(ctx context.Context, supplied string) string {
	if supplied != "" {
		return supplied
	}
	var id string
	if err := r.db.GetContext(ctx, &id, `SELECT device_id FROM pairing_identity WHERE id = 1`); err != nil || id == "" {
		return "desktop"
	}
	r.sourceOnce.Do(func() {
		for _, table := range []string{"listening_sessions", "playback_attempts", "daily_track_listening_stats", "daily_playback_attempt_stats"} {
			_, _ = r.db.ExecContext(context.Background(), `UPDATE `+table+` SET source_device_id=? WHERE source_device_id='desktop'`, id)
		}
	})
	return id
}

func (r *listeningRepository) RecordSession(ctx context.Context, s domain.ListeningSession) error {
	if s.TrackID == "" || s.ListenedSeconds <= 0 {
		return nil
	}
	source := r.sourceID(ctx, s.SourceDeviceID)
	id := s.ID
	if id == "" {
		id = uuid.NewString()
	}
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin listening session transaction: %w", err)
	}
	defer func() { _ = tx.Rollback() }()
	inserted, err := tx.ExecContext(ctx, `INSERT INTO listening_sessions (id, source_device_id, track_id, started_at, ended_at, listened_seconds, qualified_play) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO NOTHING`, id, source, s.TrackID, s.StartedAt, s.EndedAt, s.ListenedSeconds, s.QualifiedPlay)
	if err != nil {
		return fmt.Errorf("insert listening session: %w", err)
	}
	if n, rowsErr := inserted.RowsAffected(); rowsErr != nil {
		return fmt.Errorf("read inserted listening session: %w", rowsErr)
	} else if n == 0 {
		return tx.Commit()
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
		if _, err = tx.ExecContext(ctx, `INSERT INTO daily_track_listening_stats (source_device_id, local_date, track_id, listened_seconds, play_count) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(source_device_id, local_date, track_id) DO UPDATE SET listened_seconds = listened_seconds + excluded.listened_seconds, play_count = play_count + excluded.play_count`, source, date, s.TrackID, seconds, datePlay); err != nil {
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
	if _, err := r.db.ExecContext(ctx, `DELETE FROM playback_attempts WHERE started_at < ?`, before); err != nil {
		return fmt.Errorf("cleanup playback attempts: %w", err)
	}
	return nil
}

func (r *listeningRepository) RecordAttemptStart(ctx context.Context, a domain.PlaybackAttempt) error {
	if a.ID == "" || a.TrackID == "" {
		return nil
	}
	source := r.sourceID(ctx, a.SourceDeviceID)
	result, err := r.db.ExecContext(ctx, `INSERT INTO playback_attempts (id, source_device_id, track_id, started_at, start_position_seconds) VALUES (?, ?, ?, ?, ?)
		ON CONFLICT(id) DO NOTHING`, a.ID, source, a.TrackID, a.StartedAt, a.StartPositionSeconds)
	if err != nil {
		return fmt.Errorf("insert playback attempt: %w", err)
	}
	inserted, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("read inserted playback attempt: %w", err)
	}
	if inserted == 0 {
		return nil
	}
	date := a.StartedAt.In(time.Local).Format("2006-01-02")
	if _, err := r.db.ExecContext(ctx, `INSERT INTO daily_playback_attempt_stats (source_device_id, local_date, attempts) VALUES (?, ?, 1)
		ON CONFLICT(source_device_id, local_date) DO UPDATE SET attempts = attempts + 1`, source, date); err != nil {
		return fmt.Errorf("aggregate playback attempt start: %w", err)
	}
	return nil
}

func (r *listeningRepository) FinalizeAttempt(ctx context.Context, a domain.PlaybackAttempt) error {
	if a.ID == "" || a.TrackID == "" || a.EndReason == "" {
		return nil
	}
	source := r.sourceID(ctx, a.SourceDeviceID)
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin finalize playback attempt: %w", err)
	}
	defer func() { _ = tx.Rollback() }()
	result, err := tx.ExecContext(ctx, `UPDATE playback_attempts SET ended_at = ?, listened_seconds = ?, end_reason = ? WHERE id = ? AND end_reason IS NULL`, a.EndedAt, a.ListenedSeconds, a.EndReason, a.ID)
	if err != nil {
		return fmt.Errorf("finalize playback attempt: %w", err)
	}
	changed, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("read finalized playback attempt: %w", err)
	}
	if changed > 0 {
		if err = tx.GetContext(ctx, &source, `SELECT source_device_id FROM playback_attempts WHERE id = ?`, a.ID); err != nil {
			return fmt.Errorf("read playback attempt source: %w", err)
		}
	}
	if changed == 0 {
		var existing int
		if err = tx.GetContext(ctx, &existing, `SELECT COUNT(*) FROM playback_attempts WHERE id = ?`, a.ID); err != nil {
			return fmt.Errorf("check playback attempt: %w", err)
		}
		if existing > 0 {
			return nil // already finalized; a duplicate worker message is a no-op.
		}
		if _, err = tx.ExecContext(ctx, `INSERT INTO playback_attempts (id, source_device_id, track_id, started_at, ended_at, start_position_seconds, listened_seconds, end_reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`, a.ID, source, a.TrackID, a.StartedAt, a.EndedAt, a.StartPositionSeconds, a.ListenedSeconds, a.EndReason); err != nil {
			return fmt.Errorf("upsert finalized playback attempt: %w", err)
		}
		// A dropped start message must still contribute one attempt.
		if _, err = tx.ExecContext(ctx, `INSERT INTO daily_playback_attempt_stats (source_device_id, local_date, attempts) VALUES (?, ?, 1)
			ON CONFLICT(source_device_id, local_date) DO UPDATE SET attempts = attempts + 1`, source, a.StartedAt.In(time.Local).Format("2006-01-02")); err != nil {
			return fmt.Errorf("aggregate recovered attempt start: %w", err)
		}
	}
	date := a.StartedAt.In(time.Local).Format("2006-01-02")
	column := string(a.EndReason)
	if column != string(domain.PlaybackEndCompleted) && column != string(domain.PlaybackEndSkipped) && column != string(domain.PlaybackEndStopped) {
		return fmt.Errorf("invalid playback end reason %q", a.EndReason)
	}
	if _, err = tx.ExecContext(ctx, `INSERT INTO daily_playback_attempt_stats (source_device_id, local_date, `+column+`, listened_seconds) VALUES (?, ?, 1, ?)
		ON CONFLICT(source_device_id, local_date) DO UPDATE SET `+column+` = `+column+` + 1, listened_seconds = listened_seconds + excluded.listened_seconds`, source, date, a.ListenedSeconds); err != nil {
		return fmt.Errorf("aggregate finalized playback attempt: %w", err)
	}
	if err = tx.Commit(); err != nil {
		return fmt.Errorf("commit finalized playback attempt: %w", err)
	}
	return nil
}

func (r *listeningRepository) RecoverOpenAttempts(ctx context.Context) error {
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin recover playback attempts: %w", err)
	}
	defer func() { _ = tx.Rollback() }()
	var rows []struct {
		Date   string `db:"date"`
		Source string `db:"source"`
	}
	if err = tx.SelectContext(ctx, &rows, `SELECT strftime('%Y-%m-%d', started_at, 'localtime') date, source_device_id source FROM playback_attempts WHERE end_reason IS NULL`); err != nil {
		return fmt.Errorf("list open playback attempts: %w", err)
	}
	if _, err = tx.ExecContext(ctx, `UPDATE playback_attempts SET ended_at = started_at, listened_seconds = 0, end_reason = 'stopped' WHERE end_reason IS NULL`); err != nil {
		return fmt.Errorf("recover playback attempts: %w", err)
	}
	for _, row := range rows {
		if _, err = tx.ExecContext(ctx, `INSERT INTO daily_playback_attempt_stats (source_device_id, local_date, stopped) VALUES (?, ?, 1) ON CONFLICT(source_device_id, local_date) DO UPDATE SET stopped = stopped + 1`, row.Source, row.Date); err != nil {
			return fmt.Errorf("aggregate recovered playback attempt: %w", err)
		}
	}
	if err = tx.Commit(); err != nil {
		return fmt.Errorf("commit recovered playback attempts: %w", err)
	}
	return nil
}

func (r *listeningRepository) ExportSnapshot(ctx context.Context, reconciliationID string, since time.Time) (*domain.ListeningSyncSnapshot, error) {
	result := &domain.ListeningSyncSnapshot{Version: 1, ReconciliationID: reconciliationID, Sessions: []domain.ListeningSyncSession{}, Attempts: []domain.ListeningSyncAttempt{}, DailyTracks: []domain.DailyTrackListeningStat{}, DailyAttempts: []domain.DailyPlaybackAttemptStat{}}
	var sessions []struct {
		ID              string    `db:"id"`
		SourceDeviceID  string    `db:"source_device_id"`
		TrackID         string    `db:"track_id"`
		StartedAt       time.Time `db:"started_at"`
		EndedAt         time.Time `db:"ended_at"`
		ListenedSeconds int       `db:"listened_seconds"`
		QualifiedPlay   bool      `db:"qualified_play"`
	}
	if err := r.db.SelectContext(ctx, &sessions, `SELECT id, source_device_id, track_id, started_at, ended_at, listened_seconds, qualified_play FROM listening_sessions WHERE ended_at >= ?`, since); err != nil {
		return nil, fmt.Errorf("export listening sessions: %w", err)
	}
	for _, row := range sessions {
		result.Sessions = append(result.Sessions, domain.ListeningSyncSession{ID: row.ID, SourceDeviceID: row.SourceDeviceID, TrackID: row.TrackID, StartedAt: row.StartedAt.UnixMilli(), EndedAt: row.EndedAt.UnixMilli(), ListenedSeconds: row.ListenedSeconds, QualifiedPlay: row.QualifiedPlay})
	}
	var attempts []struct {
		ID                   string    `db:"id"`
		SourceDeviceID       string    `db:"source_device_id"`
		TrackID              string    `db:"track_id"`
		StartedAt            time.Time `db:"started_at"`
		EndedAt              time.Time `db:"ended_at"`
		StartPositionSeconds float64   `db:"start_position_seconds"`
		ListenedSeconds      int       `db:"listened_seconds"`
		EndReason            string    `db:"end_reason"`
	}
	if err := r.db.SelectContext(ctx, &attempts, `SELECT id, source_device_id, track_id, started_at, ended_at, start_position_seconds, listened_seconds, end_reason FROM playback_attempts WHERE ended_at >= ? AND end_reason IS NOT NULL`, since); err != nil {
		return nil, fmt.Errorf("export playback attempts: %w", err)
	}
	for _, row := range attempts {
		result.Attempts = append(result.Attempts, domain.ListeningSyncAttempt{ID: row.ID, SourceDeviceID: row.SourceDeviceID, TrackID: row.TrackID, StartedAt: row.StartedAt.UnixMilli(), EndedAt: row.EndedAt.UnixMilli(), StartPositionMS: int64(row.StartPositionSeconds * 1000), ListenedSeconds: row.ListenedSeconds, EndReason: row.EndReason})
	}
	if err := r.db.SelectContext(ctx, &result.DailyTracks, `SELECT source_device_id, local_date, track_id, listened_seconds, play_count FROM daily_track_listening_stats`); err != nil {
		return nil, fmt.Errorf("export daily track stats: %w", err)
	}
	if err := r.db.SelectContext(ctx, &result.DailyAttempts, `SELECT source_device_id, local_date, attempts, completed, skipped, stopped, listened_seconds FROM daily_playback_attempt_stats`); err != nil {
		return nil, fmt.Errorf("export daily attempt stats: %w", err)
	}
	return result, nil
}

func (r *listeningRepository) ImportSnapshot(ctx context.Context, snapshot *domain.ListeningSyncSnapshot) error {
	if snapshot == nil || snapshot.Version != 1 {
		return fmt.Errorf("invalid listening snapshot")
	}
	tx, err := r.db.BeginTxx(ctx, nil)
	if err != nil {
		return fmt.Errorf("begin listening import: %w", err)
	}
	defer func() { _ = tx.Rollback() }()
	for _, row := range snapshot.Sessions {
		_, err = tx.ExecContext(ctx, `INSERT INTO listening_sessions(id,source_device_id,track_id,started_at,ended_at,listened_seconds,qualified_play) SELECT ?,?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM tracks WHERE id=?) ON CONFLICT(id) DO NOTHING`, row.ID, row.SourceDeviceID, row.TrackID, time.UnixMilli(row.StartedAt), time.UnixMilli(row.EndedAt), row.ListenedSeconds, row.QualifiedPlay, row.TrackID)
		if err != nil {
			return fmt.Errorf("import listening session: %w", err)
		}
	}
	for _, row := range snapshot.Attempts {
		_, err = tx.ExecContext(ctx, `INSERT INTO playback_attempts(id,source_device_id,track_id,started_at,ended_at,start_position_seconds,listened_seconds,end_reason) SELECT ?,?,?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM tracks WHERE id=?) ON CONFLICT(id) DO NOTHING`, row.ID, row.SourceDeviceID, row.TrackID, time.UnixMilli(row.StartedAt), time.UnixMilli(row.EndedAt), float64(row.StartPositionMS)/1000, row.ListenedSeconds, row.EndReason, row.TrackID)
		if err != nil {
			return fmt.Errorf("import playback attempt: %w", err)
		}
	}
	for _, row := range snapshot.DailyTracks {
		var previous int
		getErr := tx.GetContext(ctx, &previous, `SELECT play_count FROM daily_track_listening_stats WHERE source_device_id=? AND local_date=? AND track_id=?`, row.SourceDeviceID, row.LocalDate, row.TrackID)
		if getErr != nil && getErr != sql.ErrNoRows {
			return fmt.Errorf("read imported play count: %w", getErr)
		}
		result, execErr := tx.ExecContext(ctx, `INSERT INTO daily_track_listening_stats(source_device_id,local_date,track_id,listened_seconds,play_count) SELECT ?,?,?,?,? WHERE EXISTS(SELECT 1 FROM tracks WHERE id=?) ON CONFLICT(source_device_id,local_date,track_id) DO UPDATE SET listened_seconds=MAX(listened_seconds,excluded.listened_seconds), play_count=MAX(play_count,excluded.play_count)`, row.SourceDeviceID, row.LocalDate, row.TrackID, row.ListenedSeconds, row.PlayCount, row.TrackID)
		if execErr != nil {
			return fmt.Errorf("import daily track stat: %w", execErr)
		}
		if changed, _ := result.RowsAffected(); changed > 0 && row.PlayCount > previous {
			if _, execErr = tx.ExecContext(ctx, `UPDATE tracks SET play_count=play_count+? WHERE id=?`, row.PlayCount-previous, row.TrackID); execErr != nil {
				return fmt.Errorf("merge imported play count: %w", execErr)
			}
		}
	}
	for _, row := range snapshot.DailyAttempts {
		_, err = tx.ExecContext(ctx, `INSERT INTO daily_playback_attempt_stats(source_device_id,local_date,attempts,completed,skipped,stopped,listened_seconds) VALUES(?,?,?,?,?,?,?) ON CONFLICT(source_device_id,local_date) DO UPDATE SET attempts=MAX(attempts,excluded.attempts), completed=MAX(completed,excluded.completed), skipped=MAX(skipped,excluded.skipped), stopped=MAX(stopped,excluded.stopped), listened_seconds=MAX(listened_seconds,excluded.listened_seconds)`, row.SourceDeviceID, row.LocalDate, row.Attempts, row.Completed, row.Skipped, row.Stopped, row.ListenedSeconds)
		if err != nil {
			return fmt.Errorf("import daily attempt stat: %w", err)
		}
	}
	if err = tx.Commit(); err != nil {
		return fmt.Errorf("commit listening import: %w", err)
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
	result := &domain.AnalyticsInsights{LibraryGrowth: []domain.AnalyticsLibraryGrowthPoint{}, Activity: []domain.AnalyticsPoint{}, Quality: []domain.AnalyticsQualityBucket{}, Genres: []domain.AnalyticsGenre{}, TopArtists: []domain.AnalyticsArtist{}, TopTracks: []domain.AnalyticsTrack{}}
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
	attemptWhere := ""
	if bounded {
		attemptWhere = "WHERE local_date >= ?"
	}
	var attemptSeconds int
	if err := r.db.QueryRowxContext(ctx, `SELECT COALESCE(SUM(attempts), 0), COALESCE(SUM(completed), 0), COALESCE(SUM(skipped), 0), COALESCE(SUM(stopped), 0), COALESCE(SUM(listened_seconds), 0) FROM daily_playback_attempt_stats `+attemptWhere, args...).Scan(&result.Attempts, &result.Completed, &result.Skipped, &result.Stopped, &attemptSeconds); err != nil {
		return nil, fmt.Errorf("sum playback attempts: %w", err)
	}
	if denominator := result.Completed + result.Skipped + result.Stopped; denominator > 0 {
		completion, skip := float64(result.Completed)*100/float64(denominator), float64(result.Skipped)*100/float64(denominator)
		result.CompletionRate, result.SkipRate = &completion, &skip
		result.AverageSessionSeconds = attemptSeconds / denominator
	}
	if result.StreakDays, err = r.currentListeningStreak(ctx, now); err != nil {
		return nil, err
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
	if result.LibraryGrowth, err = r.libraryGrowth(ctx, period, now); err != nil {
		return nil, err
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

	artistSQL := `SELECT a.id, a.name, COALESCE(a.artwork_key_manual, a.artwork_key_local, a.artwork_key_online, '') artwork_key, SUM(d.listened_seconds) listened_seconds FROM daily_track_listening_stats d JOIN track_artists ta ON ta.track_id=d.track_id JOIN artists a ON a.id=ta.artist_id ` + strings.Replace(where, "local_date", "d.local_date", 1) + fmt.Sprintf(` GROUP BY a.id ORDER BY listened_seconds DESC, a.name LIMIT %d`, listeningInsightsTopItemsLimit)
	if err := r.db.SelectContext(ctx, &result.TopArtists, artistSQL, args...); err != nil {
		return nil, fmt.Errorf("read top artists: %w", err)
	}
	trackSQL := `SELECT t.id, t.title,
		COALESCE((SELECT GROUP_CONCAT(name, ', ') FROM (SELECT a.name FROM track_artists ta JOIN artists a ON a.id=ta.artist_id WHERE ta.track_id=t.id ORDER BY ta.position)), '') artist,
		stats.play_count, stats.listened_seconds
		FROM (SELECT d.track_id, SUM(d.play_count) play_count, SUM(d.listened_seconds) listened_seconds FROM daily_track_listening_stats d ` + strings.Replace(where, "local_date", "d.local_date", 1) + fmt.Sprintf(` GROUP BY d.track_id) stats
		JOIN tracks t ON t.id=stats.track_id ORDER BY stats.play_count DESC, stats.listened_seconds DESC, t.title LIMIT %d`, listeningInsightsTopItemsLimit)
	if err := r.db.SelectContext(ctx, &result.TopTracks, trackSQL, args...); err != nil {
		return nil, fmt.Errorf("read top tracks: %w", err)
	}
	return result, nil
}

func (r *listeningRepository) currentListeningStreak(ctx context.Context, now time.Time) (int, error) {
	today := now.In(time.Local)
	var dates []string
	if err := r.db.SelectContext(ctx, &dates, `SELECT local_date FROM daily_track_listening_stats WHERE local_date <= ? GROUP BY local_date HAVING SUM(listened_seconds) > 0 ORDER BY local_date DESC`, today.Format("2006-01-02")); err != nil {
		return 0, fmt.Errorf("read listening streak dates: %w", err)
	}

	activeDates := make(map[string]struct{}, len(dates))
	for _, date := range dates {
		activeDates[date] = struct{}{}
	}
	if _, listenedToday := activeDates[today.Format("2006-01-02")]; !listenedToday {
		today = today.AddDate(0, 0, -1)
	}

	streak := 0
	for {
		if _, listened := activeDates[today.Format("2006-01-02")]; !listened {
			return streak, nil
		}
		streak++
		today = today.AddDate(0, 0, -1)
	}
}

func (r *listeningRepository) libraryGrowth(ctx context.Context, period domain.ListeningRange, now time.Time) ([]domain.AnalyticsLibraryGrowthPoint, error) {
	localNow := now.In(time.Local)
	if period == domain.ListeningRangeAll {
		var rows []struct {
			Date       string `db:"date"`
			TrackCount int    `db:"track_count"`
		}
		if err := r.db.SelectContext(ctx, &rows, `SELECT strftime('%Y', created_at, 'localtime') AS date, COUNT(*) AS track_count FROM tracks GROUP BY date ORDER BY date`); err != nil {
			return nil, fmt.Errorf("read yearly library growth: %w", err)
		}
		if len(rows) == 0 {
			return []domain.AnalyticsLibraryGrowthPoint{}, nil
		}
		byYear := make(map[int]int, len(rows))
		firstYear := localNow.Year()
		for _, row := range rows {
			parsed, err := time.Parse("2006", row.Date)
			if err != nil {
				return nil, fmt.Errorf("parse library growth year %q: %w", row.Date, err)
			}
			year := parsed.Year()
			byYear[year] = row.TrackCount
			if year < firstYear {
				firstYear = year
			}
		}
		growth := make([]domain.AnalyticsLibraryGrowthPoint, 0, localNow.Year()-firstYear+1)
		total := 0
		for year := firstYear; year <= localNow.Year(); year++ {
			total += byYear[year]
			growth = append(growth, domain.AnalyticsLibraryGrowthPoint{Date: fmt.Sprintf("%04d", year), TrackCount: total})
		}
		return growth, nil
	}

	start, _, err := periodStart(period, localNow)
	if err != nil {
		return nil, err
	}
	end := time.Date(localNow.Year(), localNow.Month(), localNow.Day()+1, 0, 0, 0, 0, time.Local)
	var base int
	if err := r.db.GetContext(ctx, &base, `SELECT COUNT(*) FROM tracks WHERE created_at < ?`, start); err != nil {
		return nil, fmt.Errorf("read library growth baseline: %w", err)
	}
	var rows []struct {
		Date       string `db:"date"`
		TrackCount int    `db:"track_count"`
	}
	if err := r.db.SelectContext(ctx, &rows, `SELECT date(created_at, 'localtime') AS date, COUNT(*) AS track_count FROM tracks WHERE created_at >= ? AND created_at < ? GROUP BY date ORDER BY date`, start, end); err != nil {
		return nil, fmt.Errorf("read daily library growth: %w", err)
	}
	byDate := make(map[string]int, len(rows))
	for _, row := range rows {
		byDate[row.Date] = row.TrackCount
	}
	growth := make([]domain.AnalyticsLibraryGrowthPoint, 0, int(end.Sub(start).Hours()/24))
	for day := start; day.Before(end); day = day.AddDate(0, 0, 1) {
		date := day.Format("2006-01-02")
		base += byDate[date]
		growth = append(growth, domain.AnalyticsLibraryGrowthPoint{Date: date, TrackCount: base})
	}
	return growth, nil
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
