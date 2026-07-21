package sqlite

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"

	"airmedy/internal/domain"
)

// marshalDelimiters encodes a delimiter list as JSON for TEXT storage. An empty
// list is preserved (encoded as "[]") since the user may intentionally disable
// splitting for a field.
func marshalDelimiters(list []string) string {
	if list == nil {
		list = []string{}
	}
	b, err := json.Marshal(list)
	if err != nil {
		b, _ = json.Marshal(domain.DefaultDelimiters())
	}
	return string(b)
}

// unmarshalDelimiters decodes a stored delimiter list. A genuinely absent value
// (NULL column / empty string) falls back to the defaults; a valid but empty
// JSON array ("[]") is preserved as an intentional "do not split" choice.
func unmarshalDelimiters(s string) []string {
	if s == "" {
		return domain.DefaultDelimiters()
	}
	var list []string
	if err := json.Unmarshal([]byte(s), &list); err != nil {
		return domain.DefaultDelimiters()
	}
	if list == nil {
		list = []string{}
	}
	return list
}

// normalizeSyncInterval coerces a stored library sync interval to a known value,
// falling back to the default for empty or unrecognized data.
func normalizeSyncInterval(s string) string {
	switch s {
	case domain.SyncInterval15s, domain.SyncInterval15m, domain.SyncInterval30m, domain.SyncInterval1h,
		domain.SyncIntervalLaunch, domain.SyncIntervalManual:
		return s
	default:
		return domain.DefaultSyncInterval
	}
}

func primaryColorOrDefault(color string) string {
	normalized, err := domain.NormalizePrimaryColor(color)
	if err != nil {
		return domain.DefaultPrimaryColor
	}
	return normalized
}

type settingsRepository struct {
	db *DB
}

func NewSettingsRepository(db *DB) domain.SettingsRepository {
	return &settingsRepository{db: db}
}

func (r *settingsRepository) Save(ctx context.Context, settings *domain.AppSettings) error {
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO app_settings (id, language, theme, primary_color, lastfm_username, auto_check_update, start_at_login, show_tray_icon, eq_enabled, use_online_artist_artwork, prefer_local_artist_artwork, last_scan_version, enable_lrclib, enable_kugou, prefer_local_lyrics, lyrics_folder_enabled, lyrics_folder_path, lyrics_subfolder_enabled, lyrics_subfolder_name, prevent_sleep_while_playing, remote_server_enabled, remote_server_port, remote_server_password, show_player_indicator, auto_advance_notifications_enabled, library_sync_interval, library_analysis_enabled, library_analysis_worker_count, normalization_enabled, normalization_mode, normalization_target_lufs, normalization_prevent_clip, artist_delimiters, album_artist_delimiters, genre_delimiters, composer_delimiters, mood_derivation_version, max_queue_size, crossfade_seconds, blend_artwork_during_crossfade, high_contrast_lyrics, eq_preamp, stereo_width, updated_at)
		 VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   language = excluded.language,
		   theme = excluded.theme,
		   primary_color = excluded.primary_color,
		   lastfm_username = excluded.lastfm_username,
		   auto_check_update = excluded.auto_check_update,
		   start_at_login = excluded.start_at_login,
		   show_tray_icon = excluded.show_tray_icon,
		   eq_enabled = excluded.eq_enabled,
		   use_online_artist_artwork = excluded.use_online_artist_artwork,
		   prefer_local_artist_artwork = excluded.prefer_local_artist_artwork,
		   last_scan_version = excluded.last_scan_version,
		   enable_lrclib = excluded.enable_lrclib,
		   enable_kugou = excluded.enable_kugou,
		   prefer_local_lyrics = excluded.prefer_local_lyrics,
		   lyrics_folder_enabled = excluded.lyrics_folder_enabled,
		   lyrics_folder_path = excluded.lyrics_folder_path,
		   lyrics_subfolder_enabled = excluded.lyrics_subfolder_enabled,
		   lyrics_subfolder_name = excluded.lyrics_subfolder_name,
		   prevent_sleep_while_playing = excluded.prevent_sleep_while_playing,
		   remote_server_enabled = excluded.remote_server_enabled,
		   remote_server_port = excluded.remote_server_port,
		   remote_server_password = excluded.remote_server_password,
		   show_player_indicator = excluded.show_player_indicator,
		   auto_advance_notifications_enabled = excluded.auto_advance_notifications_enabled,
		   library_sync_interval = excluded.library_sync_interval,
		   library_analysis_enabled = excluded.library_analysis_enabled,
		   library_analysis_worker_count = excluded.library_analysis_worker_count,
		   normalization_enabled = excluded.normalization_enabled,
		   normalization_mode = excluded.normalization_mode,
		   normalization_target_lufs = excluded.normalization_target_lufs,
		   normalization_prevent_clip = excluded.normalization_prevent_clip,
		   artist_delimiters = excluded.artist_delimiters,
		   album_artist_delimiters = excluded.album_artist_delimiters,
		   genre_delimiters = excluded.genre_delimiters,
		   composer_delimiters = excluded.composer_delimiters,
		   mood_derivation_version = excluded.mood_derivation_version,
		   max_queue_size = excluded.max_queue_size,
		   crossfade_seconds = excluded.crossfade_seconds,
		   blend_artwork_during_crossfade = excluded.blend_artwork_during_crossfade,
		   high_contrast_lyrics = excluded.high_contrast_lyrics,
		   eq_preamp = excluded.eq_preamp,
		   stereo_width = excluded.stereo_width,
		   updated_at = excluded.updated_at`,
		settings.Language,
		settings.Theme,
		settings.PrimaryColor,
		settings.LastFmUsername,
		settings.AutoCheckUpdate,
		settings.StartAtLogin,
		settings.ShowTrayIcon,
		settings.EQEnabled,
		settings.UseOnlineArtistArtwork,
		settings.PreferLocalArtistArtwork,
		settings.LastScanVersion,
		settings.EnableLrclib,
		settings.EnableKugou,
		settings.PreferLocalLyrics,
		settings.LyricsFolderEnabled,
		settings.LyricsFolderPath,
		settings.LyricsSubfolderEnabled,
		settings.LyricsSubfolderName,
		settings.PreventSleepWhilePlaying,
		settings.RemoteServerEnabled,
		settings.RemoteServerPort,
		settings.RemoteServerPassword,
		settings.ShowPlayerIndicator,
		settings.AutoAdvanceNotificationsEnabled,
		settings.LibrarySyncInterval,
		settings.LibraryAnalysisEnabled,
		settings.LibraryAnalysisWorkerCount,
		settings.NormalizationEnabled,
		settings.NormalizationMode,
		settings.NormalizationTargetLUFS,
		settings.NormalizationPreventClip,
		marshalDelimiters(settings.ArtistDelimiters),
		marshalDelimiters(settings.AlbumArtistDelimiters),
		marshalDelimiters(settings.GenreDelimiters),
		marshalDelimiters(settings.ComposerDelimiters),
		settings.MoodDerivationVersion,
		settings.MaxQueueSize,
		domain.ClampCrossfadeSeconds(settings.CrossfadeSeconds),
		settings.BlendArtworkDuringCrossfade,
		settings.HighContrastLyrics,
		settings.EQPreamp,
		settings.StereoWidth,
	)
	if err != nil {
		return fmt.Errorf("failed to save app settings: %w", err)
	}
	return nil
}

func (r *settingsRepository) Load(ctx context.Context) (*domain.AppSettings, error) {
	var row struct {
		Language                        string         `db:"language"`
		Theme                           string         `db:"theme"`
		PrimaryColor                    string         `db:"primary_color"`
		LastFmUsername                  sql.NullString `db:"lastfm_username"`
		AutoCheckUpdate                 bool           `db:"auto_check_update"`
		StartAtLogin                    bool           `db:"start_at_login"`
		ShowTrayIcon                    bool           `db:"show_tray_icon"`
		EQEnabled                       bool           `db:"eq_enabled"`
		UseOnlineArtistArtwork          bool           `db:"use_online_artist_artwork"`
		PreferLocalArtistArtwork        bool           `db:"prefer_local_artist_artwork"`
		LastScanVersion                 string         `db:"last_scan_version"`
		EnableLrclib                    bool           `db:"enable_lrclib"`
		EnableKugou                     bool           `db:"enable_kugou"`
		PreferLocalLyrics               bool           `db:"prefer_local_lyrics"`
		LyricsFolderEnabled             bool           `db:"lyrics_folder_enabled"`
		LyricsFolderPath                sql.NullString `db:"lyrics_folder_path"`
		LyricsSubfolderEnabled          bool           `db:"lyrics_subfolder_enabled"`
		LyricsSubfolderName             sql.NullString `db:"lyrics_subfolder_name"`
		PreventSleepWhilePlaying        bool           `db:"prevent_sleep_while_playing"`
		RemoteServerEnabled             bool           `db:"remote_server_enabled"`
		RemoteServerPort                int            `db:"remote_server_port"`
		RemoteServerPassword            string         `db:"remote_server_password"`
		ShowPlayerIndicator             bool           `db:"show_player_indicator"`
		AutoAdvanceNotificationsEnabled bool           `db:"auto_advance_notifications_enabled"`
		LibrarySyncInterval             sql.NullString `db:"library_sync_interval"`
		LibraryAnalysisEnabled          bool           `db:"library_analysis_enabled"`
		LibraryAnalysisWorkerCount      int            `db:"library_analysis_worker_count"`
		NormalizationEnabled            bool           `db:"normalization_enabled"`
		NormalizationMode               string         `db:"normalization_mode"`
		NormalizationTargetLUFS         float64        `db:"normalization_target_lufs"`
		NormalizationPreventClip        bool           `db:"normalization_prevent_clip"`
		ArtistDelimiters                sql.NullString `db:"artist_delimiters"`
		AlbumArtistDelimiters           sql.NullString `db:"album_artist_delimiters"`
		GenreDelimiters                 sql.NullString `db:"genre_delimiters"`
		ComposerDelimiters              sql.NullString `db:"composer_delimiters"`
		MoodDerivationVersion           int            `db:"mood_derivation_version"`
		MaxQueueSize                    int            `db:"max_queue_size"`
		CrossfadeSeconds                int            `db:"crossfade_seconds"`
		BlendArtworkDuringCrossfade     bool           `db:"blend_artwork_during_crossfade"`
		HighContrastLyrics              bool           `db:"high_contrast_lyrics"`
		EQPreamp                        float64        `db:"eq_preamp"`
		StereoWidth                     float64        `db:"stereo_width"`
	}
	err := r.db.GetContext(ctx, &row,
		`SELECT language, theme, primary_color, lastfm_username, auto_check_update, start_at_login, show_tray_icon, eq_enabled, use_online_artist_artwork, prefer_local_artist_artwork, last_scan_version, enable_lrclib, enable_kugou, prefer_local_lyrics, lyrics_folder_enabled, lyrics_folder_path, lyrics_subfolder_enabled, lyrics_subfolder_name, prevent_sleep_while_playing, remote_server_enabled, remote_server_port, remote_server_password, show_player_indicator, auto_advance_notifications_enabled, library_sync_interval, library_analysis_enabled, library_analysis_worker_count, normalization_enabled, normalization_mode, normalization_target_lufs, normalization_prevent_clip, artist_delimiters, album_artist_delimiters, genre_delimiters, composer_delimiters, mood_derivation_version, max_queue_size, crossfade_seconds, blend_artwork_during_crossfade, high_contrast_lyrics, eq_preamp, stereo_width FROM app_settings WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return &domain.AppSettings{
			Language:                        "en",
			Theme:                           "system",
			PrimaryColor:                    domain.DefaultPrimaryColor,
			AutoCheckUpdate:                 true,
			StartAtLogin:                    false,
			ShowTrayIcon:                    true,
			EQEnabled:                       true,
			EnableLrclib:                    true,
			EnableKugou:                     true,
			PreferLocalLyrics:               true,
			UseOnlineArtistArtwork:          true,
			PreferLocalArtistArtwork:        true,
			PreventSleepWhilePlaying:        false,
			ShowPlayerIndicator:             true,
			LibrarySyncInterval:             domain.DefaultSyncInterval,
			LibraryAnalysisEnabled:          false,
			LibraryAnalysisWorkerCount:      domain.DefaultLibraryAnalysisWorkerCount,
			NormalizationEnabled:            false,
			NormalizationMode:               "track",
			NormalizationTargetLUFS:         domain.DefaultTargetLUFS,
			NormalizationPreventClip:        true,
			ArtistDelimiters:                domain.DefaultDelimiters(),
			AlbumArtistDelimiters:           domain.DefaultDelimiters(),
			GenreDelimiters:                 domain.DefaultDelimiters(),
			ComposerDelimiters:              domain.DefaultDelimiters(),
			MaxQueueSize:                    domain.DefaultMaxQueueSize,
			CrossfadeSeconds:                0,
			BlendArtworkDuringCrossfade:     true,
			HighContrastLyrics:              true,
			AutoAdvanceNotificationsEnabled: true,
			EQPreamp:                        0,
			StereoWidth:                     100,
		}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load app settings: %w", err)
	}

	return &domain.AppSettings{
		Language:                        row.Language,
		Theme:                           row.Theme,
		PrimaryColor:                    primaryColorOrDefault(row.PrimaryColor),
		LastFmUsername:                  row.LastFmUsername.String,
		AutoCheckUpdate:                 row.AutoCheckUpdate,
		StartAtLogin:                    row.StartAtLogin,
		ShowTrayIcon:                    row.ShowTrayIcon,
		EQEnabled:                       row.EQEnabled,
		EnableLrclib:                    row.EnableLrclib,
		EnableKugou:                     row.EnableKugou,
		PreferLocalLyrics:               row.PreferLocalLyrics,
		LyricsFolderEnabled:             row.LyricsFolderEnabled,
		LyricsFolderPath:                row.LyricsFolderPath.String,
		LyricsSubfolderEnabled:          row.LyricsSubfolderEnabled,
		LyricsSubfolderName:             row.LyricsSubfolderName.String,
		UseOnlineArtistArtwork:          row.UseOnlineArtistArtwork,
		PreferLocalArtistArtwork:        row.PreferLocalArtistArtwork,
		LastScanVersion:                 row.LastScanVersion,
		PreventSleepWhilePlaying:        row.PreventSleepWhilePlaying,
		RemoteServerEnabled:             row.RemoteServerEnabled,
		RemoteServerPort:                row.RemoteServerPort,
		RemoteServerPassword:            row.RemoteServerPassword,
		ShowPlayerIndicator:             row.ShowPlayerIndicator,
		AutoAdvanceNotificationsEnabled: row.AutoAdvanceNotificationsEnabled,
		LibrarySyncInterval:             normalizeSyncInterval(row.LibrarySyncInterval.String),
		LibraryAnalysisEnabled:          row.LibraryAnalysisEnabled,
		LibraryAnalysisWorkerCount:      row.LibraryAnalysisWorkerCount,
		NormalizationEnabled:            row.NormalizationEnabled,
		NormalizationMode:               row.NormalizationMode,
		NormalizationTargetLUFS:         row.NormalizationTargetLUFS,
		NormalizationPreventClip:        row.NormalizationPreventClip,
		ArtistDelimiters:                unmarshalDelimiters(row.ArtistDelimiters.String),
		AlbumArtistDelimiters:           unmarshalDelimiters(row.AlbumArtistDelimiters.String),
		GenreDelimiters:                 unmarshalDelimiters(row.GenreDelimiters.String),
		ComposerDelimiters:              unmarshalDelimiters(row.ComposerDelimiters.String),
		MoodDerivationVersion:           row.MoodDerivationVersion,
		MaxQueueSize:                    domain.ResolveMaxQueueSize(row.MaxQueueSize),
		CrossfadeSeconds:                domain.ClampCrossfadeSeconds(row.CrossfadeSeconds),
		BlendArtworkDuringCrossfade:     row.BlendArtworkDuringCrossfade,
		HighContrastLyrics:              row.HighContrastLyrics,
		EQPreamp:                        row.EQPreamp,
		StereoWidth:                     row.StereoWidth,
	}, nil
}
