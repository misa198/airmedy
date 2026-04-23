package wails

import (
	"context"

	"airmedy/internal/app/playlist"
	"airmedy/internal/domain"
)

type PlaylistService struct {
	service *playlist.PlaylistService
}

func NewPlaylistService(service *playlist.PlaylistService) *PlaylistService {
	return &PlaylistService{service: service}
}

func (s *PlaylistService) CreatePlaylist(name, description string) (*domain.Playlist, error) {
	return s.service.Create(context.Background(), name, description)
}

func (s *PlaylistService) UpdatePlaylist(id, name, description string) error {
	return s.service.Update(context.Background(), id, name, description)
}

func (s *PlaylistService) DeletePlaylist(id string) error {
	return s.service.Delete(context.Background(), id)
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

func (s *PlaylistService) AddTrackToPlaylist(playlistID, trackID string) error {
	return s.service.AddTrack(context.Background(), playlistID, trackID)
}

func (s *PlaylistService) RemoveTrackFromPlaylist(playlistID, trackID string) error {
	return s.service.RemoveTrack(context.Background(), playlistID, trackID)
}
