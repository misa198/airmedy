package wails

import (
	"context"
	"fmt"

	"airmedy/internal/app/library"
	"airmedy/internal/app/playlist"
	"airmedy/internal/domain"

	"github.com/wailsapp/wails/v3/pkg/application"
)

// M3U8Preview is returned to the frontend so it can populate the import dialog
// before the user confirms.
type M3U8Preview struct {
	FilePath     string `json:"file_path"`
	PlaylistName string `json:"playlist_name"`
	EntryCount   int    `json:"entry_count"`
}

// M3U8ImportResult reports how many tracks were imported and how many were skipped.
type M3U8ImportResult struct {
	PlaylistID    string `json:"playlist_id"`
	ImportedCount int    `json:"imported_count"`
	SkippedCount  int    `json:"skipped_count"`
}

type PlaylistService struct {
	service        *playlist.PlaylistService
	libraryService *library.LibraryService
}

func NewPlaylistService(service *playlist.PlaylistService, libraryService *library.LibraryService) *PlaylistService {
	return &PlaylistService{service: service, libraryService: libraryService}
}

func (s *PlaylistService) CreatePlaylist(name, description string) (*domain.Playlist, error) {
	return s.service.Create(context.Background(), name, description)
}

func (s *PlaylistService) UpdatePlaylist(id, name, description string) error {
	err := s.service.Update(context.Background(), id, name, description)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("playlist:renamed", id)
		}
	}
	return err
}

func (s *PlaylistService) DeletePlaylist(id string) error {
	err := s.service.Delete(context.Background(), id)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("playlist:deleted", id)
		}
	}
	return err
}

func (s *PlaylistService) GetAllPlaylists() ([]*domain.Playlist, error) {
	return s.service.GetAll(context.Background())
}

func (s *PlaylistService) GetPlaylistByID(id string) (*domain.Playlist, error) {
	return s.service.GetByID(context.Background(), id)
}

func (s *PlaylistService) GetPlaylistTracks(playlistID string) ([]*domain.TrackDTO, error) {
	return s.service.GetTracks(context.Background(), playlistID)
}

func (s *PlaylistService) GetPlaylistsForTrack(trackID string) ([]string, error) {
	return s.service.GetPlaylistsForTrack(context.Background(), trackID)
}

type PlaylistTracksChangedEvent struct {
	PlaylistID string `json:"playlist_id"`
	SenderID   string `json:"sender_id"`
}

func (s *PlaylistService) AddTrackToPlaylist(playlistID, trackID, senderID string) error {
	err := s.service.AddTrack(context.Background(), playlistID, trackID)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("playlist:tracks-changed", &PlaylistTracksChangedEvent{
				PlaylistID: playlistID,
				SenderID:   senderID,
			})
		}
	}
	return err
}

func (s *PlaylistService) AddTracksToPlaylist(playlistID string, trackIDs []string, senderID string) error {
	err := s.service.AddTracks(context.Background(), playlistID, trackIDs)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("playlist:tracks-changed", &PlaylistTracksChangedEvent{
				PlaylistID: playlistID,
				SenderID:   senderID,
			})
		}
	}
	return err
}

func (s *PlaylistService) RemoveTrackFromPlaylist(playlistID, trackID, senderID string) error {
	err := s.service.RemoveTrack(context.Background(), playlistID, trackID)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("playlist:tracks-changed", &PlaylistTracksChangedEvent{
				PlaylistID: playlistID,
				SenderID:   senderID,
			})
		}
	}
	return err
}

func (s *PlaylistService) MoveTrack(playlistID, trackID, prevTrackID, nextTrackID, senderID string) error {
	err := s.service.MoveTrack(context.Background(), playlistID, trackID, prevTrackID, nextTrackID)
	if err == nil {
		if app := application.Get(); app != nil && app.Event != nil {
			app.Event.Emit("playlist:tracks-changed", &PlaylistTracksChangedEvent{
				PlaylistID: playlistID,
				SenderID:   senderID,
			})
		}
	}
	return err
}

func (s *PlaylistService) GetPlaylistColors(id string) (*domain.ThemeColors, error) {
	return s.service.GetPlaylistColors(context.Background(), id)
}

func (s *PlaylistService) RemovePlaylistArtwork(id string) error {
	return s.service.RemoveArtwork(context.Background(), id)
}

func (s *PlaylistService) ExportPlaylistToM3U8(playlistID string) error {
	app := application.Get()
	if app == nil {
		return fmt.Errorf("application not initialized")
	}

	destPath, err := app.Dialog.SaveFile().
		SetMessage("Export Playlist").
		SetFilename(playlistID + ".m3u8").
		AddFilter("M3U8 Playlist", "*.m3u8").
		PromptForSingleSelection()
	if err != nil {
		return err
	}
	if destPath == "" {
		return nil
	}

	return s.service.ExportM3U8(context.Background(), playlistID, destPath)
}

// SelectAndParseM3U8 opens an OS file-picker filtered to M3U/M3U8 files and
// parses the selected file. Returns nil without error when the user cancels.
func (s *PlaylistService) SelectAndParseM3U8() (*M3U8Preview, error) {
	app := application.Get()
	if app == nil {
		return nil, fmt.Errorf("application not initialized")
	}

	filePath, err := app.Dialog.OpenFile().
		SetTitle("Import Playlist").
		AddFilter("M3U8 Playlist", "*.m3u;*.m3u8").
		PromptForSingleSelection()
	if err != nil {
		return nil, err
	}
	if filePath == "" {
		return nil, nil
	}

	parsed, err := playlist.ParseM3U8(filePath)
	if err != nil {
		return nil, fmt.Errorf("parse m3u8: %w", err)
	}

	return &M3U8Preview{
		FilePath:     filePath,
		PlaylistName: parsed.PlaylistName,
		EntryCount:   len(parsed.Entries),
	}, nil
}

// ImportM3U8Playlist creates a new playlist from the given M3U8 file. Each
// track path is validated (exists, supported format, inside a watched folder);
// invalid paths are silently skipped. For tracks not yet in the library they
// are imported first, with M3U8 metadata used as fallback for empty tags.
func (s *PlaylistService) ImportM3U8Playlist(filePath, name string) (*M3U8ImportResult, error) {
	ctx := context.Background()

	parsed, err := playlist.ParseM3U8(filePath)
	if err != nil {
		return nil, fmt.Errorf("parse m3u8: %w", err)
	}

	p, err := s.service.Create(ctx, name, "")
	if err != nil {
		return nil, fmt.Errorf("create playlist: %w", err)
	}

	result := &M3U8ImportResult{PlaylistID: p.ID}

	var trackIDs []string
	for _, entry := range parsed.Entries {
		if entry.Path == "" {
			result.SkippedCount++
			continue
		}
		if err := s.libraryService.IsPathValid(ctx, entry.Path); err != nil {
			result.SkippedCount++
			continue
		}
		track, err := s.libraryService.EnsureTrack(ctx, entry.Path, entry.Title, entry.Artist)
		if err != nil {
			result.SkippedCount++
			continue
		}
		trackIDs = append(trackIDs, track.ID)
	}

	if err := s.service.AddTracks(ctx, p.ID, trackIDs); err != nil {
		_ = s.service.Delete(ctx, p.ID)
		return nil, fmt.Errorf("add tracks: %w", err)
	}
	result.ImportedCount = len(trackIDs)

	if result.ImportedCount == 0 {
		_ = s.service.Delete(ctx, p.ID)
		return nil, fmt.Errorf("no valid tracks found in playlist file")
	}

	if app := application.Get(); app != nil && app.Event != nil {
		app.Event.Emit("playlist:tracks-changed", &PlaylistTracksChangedEvent{
			PlaylistID: p.ID,
			SenderID:   "",
		})
	}

	return result, nil
}

func (s *PlaylistService) SelectAndSetPlaylistArtwork(id string) (string, error) {
	app := application.Get()
	if app == nil {
		return "", fmt.Errorf("application not initialized")
	}

	result, err := app.Dialog.OpenFile().
		SetTitle("Select Playlist Cover").
		AddFilter("Images", "*.jpg;*.jpeg;*.png").
		PromptForSingleSelection()

	if err != nil {
		return "", err
	}
	if result == "" {
		return "", nil
	}

	key, err := s.service.SetArtwork(context.Background(), id, result)
	if err != nil {
		return "", err
	}
	if key == nil {
		return "", nil
	}
	return *key, nil
}
