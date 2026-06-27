package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type settingsRepository struct {
	db *DB
}

func NewSettingsRepository(db *DB) domain.SettingsRepository {
	return &settingsRepository{db: db}
}

func (r *settingsRepository) Save(ctx context.Context, settings *domain.AppSettings) error {
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO app_settings (id, language, theme, lastfm_username, auto_check_update, start_at_login, show_tray_icon, eq_enabled, use_online_artist_artwork, prefer_local_artist_artwork, last_scan_version, enable_lrclib, enable_kugou, prefer_local_lyrics, lyrics_folder_enabled, lyrics_folder_path, lyrics_subfolder_enabled, lyrics_subfolder_name, prevent_sleep_while_playing, remote_server_enabled, remote_server_port, remote_server_password, show_player_indicator, updated_at)
		 VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   language = excluded.language,
		   theme = excluded.theme,
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
		   updated_at = excluded.updated_at`,
		settings.Language,
		settings.Theme,
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
	)
	if err != nil {
		return fmt.Errorf("failed to save app settings: %w", err)
	}
	return nil
}

func (r *settingsRepository) Load(ctx context.Context) (*domain.AppSettings, error) {
	var row struct {
		Language                 string         `db:"language"`
		Theme                    string         `db:"theme"`
		LastFmUsername           sql.NullString `db:"lastfm_username"`
		AutoCheckUpdate          bool           `db:"auto_check_update"`
		StartAtLogin             bool           `db:"start_at_login"`
		ShowTrayIcon             bool           `db:"show_tray_icon"`
		EQEnabled                bool           `db:"eq_enabled"`
		UseOnlineArtistArtwork   bool           `db:"use_online_artist_artwork"`
		PreferLocalArtistArtwork bool           `db:"prefer_local_artist_artwork"`
		LastScanVersion          string         `db:"last_scan_version"`
		EnableLrclib             bool           `db:"enable_lrclib"`
		EnableKugou              bool           `db:"enable_kugou"`
		PreferLocalLyrics        bool           `db:"prefer_local_lyrics"`
		LyricsFolderEnabled      bool           `db:"lyrics_folder_enabled"`
		LyricsFolderPath         sql.NullString `db:"lyrics_folder_path"`
		LyricsSubfolderEnabled   bool           `db:"lyrics_subfolder_enabled"`
		LyricsSubfolderName      sql.NullString `db:"lyrics_subfolder_name"`
		PreventSleepWhilePlaying bool           `db:"prevent_sleep_while_playing"`
		RemoteServerEnabled      bool           `db:"remote_server_enabled"`
		RemoteServerPort         int            `db:"remote_server_port"`
		RemoteServerPassword     string         `db:"remote_server_password"`
		ShowPlayerIndicator      bool           `db:"show_player_indicator"`
	}
	err := r.db.GetContext(ctx, &row,
		`SELECT language, theme, lastfm_username, auto_check_update, start_at_login, show_tray_icon, eq_enabled, use_online_artist_artwork, prefer_local_artist_artwork, last_scan_version, enable_lrclib, enable_kugou, prefer_local_lyrics, lyrics_folder_enabled, lyrics_folder_path, lyrics_subfolder_enabled, lyrics_subfolder_name, prevent_sleep_while_playing, remote_server_enabled, remote_server_port, remote_server_password, show_player_indicator FROM app_settings WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return &domain.AppSettings{
			Language:                 "en",
			Theme:                    "system",
			AutoCheckUpdate:          true,
			StartAtLogin:             false,
			ShowTrayIcon:             true,
			EQEnabled:                true,
			EnableLrclib:             true,
			EnableKugou:              true,
			PreferLocalLyrics:        true,
			UseOnlineArtistArtwork:   true,
			PreferLocalArtistArtwork: true,
			PreventSleepWhilePlaying: false,
			ShowPlayerIndicator:      true,
		}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load app settings: %w", err)
	}

	return &domain.AppSettings{
		Language:                 row.Language,
		Theme:                    row.Theme,
		LastFmUsername:           row.LastFmUsername.String,
		AutoCheckUpdate:          row.AutoCheckUpdate,
		StartAtLogin:             row.StartAtLogin,
		ShowTrayIcon:             row.ShowTrayIcon,
		EQEnabled:                row.EQEnabled,
		EnableLrclib:             row.EnableLrclib,
		EnableKugou:              row.EnableKugou,
		PreferLocalLyrics:        row.PreferLocalLyrics,
		LyricsFolderEnabled:      row.LyricsFolderEnabled,
		LyricsFolderPath:         row.LyricsFolderPath.String,
		LyricsSubfolderEnabled:   row.LyricsSubfolderEnabled,
		LyricsSubfolderName:      row.LyricsSubfolderName.String,
		UseOnlineArtistArtwork:   row.UseOnlineArtistArtwork,
		PreferLocalArtistArtwork: row.PreferLocalArtistArtwork,
		LastScanVersion:          row.LastScanVersion,
		PreventSleepWhilePlaying: row.PreventSleepWhilePlaying,
		RemoteServerEnabled:      row.RemoteServerEnabled,
		RemoteServerPort:         row.RemoteServerPort,
		RemoteServerPassword:     row.RemoteServerPassword,
		ShowPlayerIndicator:      row.ShowPlayerIndicator,
	}, nil
}
