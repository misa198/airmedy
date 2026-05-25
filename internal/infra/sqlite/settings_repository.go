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
		`INSERT INTO app_settings (id, language, theme, lastfm_username, auto_check_update, start_at_login, show_tray_icon, eq_enabled, use_online_artist_artwork, enable_lrclib, enable_kugou, prefer_metadata_lyrics, updated_at)
		 VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   language = excluded.language,
		   theme = excluded.theme,
		   lastfm_username = excluded.lastfm_username,
		   auto_check_update = excluded.auto_check_update,
		   start_at_login = excluded.start_at_login,
		   show_tray_icon = excluded.show_tray_icon,
		   eq_enabled = excluded.eq_enabled,
		   use_online_artist_artwork = excluded.use_online_artist_artwork,
		   enable_lrclib = excluded.enable_lrclib,
		   enable_kugou = excluded.enable_kugou,
		   prefer_metadata_lyrics = excluded.prefer_metadata_lyrics,
		   updated_at = excluded.updated_at`,
		settings.Language,
		settings.Theme,
		settings.LastFmUsername,
		settings.AutoCheckUpdate,
		settings.StartAtLogin,
		settings.ShowTrayIcon,
		settings.EQEnabled,
		settings.UseOnlineArtistArtwork,
		settings.EnableLrclib,
		settings.EnableKugou,
		settings.PreferMetadataLyrics,
	)
	if err != nil {
		return fmt.Errorf("failed to save app settings: %w", err)
	}
	return nil
}

func (r *settingsRepository) Load(ctx context.Context) (*domain.AppSettings, error) {
	var row struct {
		Language               string         `db:"language"`
		Theme                  string         `db:"theme"`
		LastFmUsername         sql.NullString `db:"lastfm_username"`
		AutoCheckUpdate        bool           `db:"auto_check_update"`
		StartAtLogin           bool           `db:"start_at_login"`
		ShowTrayIcon           bool           `db:"show_tray_icon"`
		EQEnabled              bool           `db:"eq_enabled"`
		UseOnlineArtistArtwork bool           `db:"use_online_artist_artwork"`
		EnableLrclib           bool           `db:"enable_lrclib"`
		EnableKugou            bool           `db:"enable_kugou"`
		PreferMetadataLyrics   bool           `db:"prefer_metadata_lyrics"`
	}
	err := r.db.GetContext(ctx, &row,
		`SELECT language, theme, lastfm_username, auto_check_update, start_at_login, show_tray_icon, eq_enabled, use_online_artist_artwork, enable_lrclib, enable_kugou, prefer_metadata_lyrics FROM app_settings WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return &domain.AppSettings{
			Language:               "en",
			Theme:                  "system",
			AutoCheckUpdate:        true,
			StartAtLogin:           false,
			ShowTrayIcon:           true,
			EQEnabled:              true,
			EnableLrclib:           true,
			EnableKugou:            true,
			PreferMetadataLyrics:   true,
			UseOnlineArtistArtwork: true,
		}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load app settings: %w", err)
	}

	return &domain.AppSettings{
		Language:               row.Language,
		Theme:                  row.Theme,
		LastFmUsername:         row.LastFmUsername.String,
		AutoCheckUpdate:        row.AutoCheckUpdate,
		StartAtLogin:           row.StartAtLogin,
		ShowTrayIcon:           row.ShowTrayIcon,
		EQEnabled:              row.EQEnabled,
		EnableLrclib:           row.EnableLrclib,
		EnableKugou:            row.EnableKugou,
		PreferMetadataLyrics:   row.PreferMetadataLyrics,
		UseOnlineArtistArtwork: row.UseOnlineArtistArtwork,
	}, nil
}
