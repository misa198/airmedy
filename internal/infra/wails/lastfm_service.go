package wails

import (
	"context"
	"airmedy/internal/app/lastfm"
)

type LastFmService struct {
	svc *lastfm.LastFmService
}

func NewLastFmService(svc *lastfm.LastFmService) *LastFmService {
	return &LastFmService{svc: svc}
}

func (s *LastFmService) GetService() *lastfm.LastFmService {
	return s.svc
}

func (s *LastFmService) Connect(ctx context.Context) error {
	return s.svc.Connect(ctx)
}

func (s *LastFmService) Disconnect(ctx context.Context) error {
	return s.svc.Disconnect(ctx)
}

type LastFmStatus struct {
	Connected bool   `json:"connected"`
	Username  string `json:"username"`
	AvatarURL string `json:"avatar_url"`
}

func (s *LastFmService) GetStatus(ctx context.Context) LastFmStatus {
	connected, username, avatarURL := s.svc.GetStatus()
	return LastFmStatus{
		Connected: connected,
		Username:  username,
		AvatarURL: avatarURL,
	}
}
