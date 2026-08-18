package mobilesync

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"time"

	playlistapp "airmedy/internal/app/playlist"
	"airmedy/internal/domain"

	"github.com/google/uuid"
)

const playlistSyncVersion = 1

type playlistMutation struct {
	MutationID string                  `json:"mutation_id"`
	PlaylistID string                  `json:"playlist_id"`
	Operation  string                  `json:"operation"`
	UpdatedAt  int64                   `json:"updated_at"`
	Payload    playlistMutationPayload `json:"payload"`
}
type playlistMutationPayload struct {
	Name            string `json:"name,omitempty"`
	Description     string `json:"description,omitempty"`
	TrackID         string `json:"track_id,omitempty"`
	PreviousTrackID string `json:"previous_track_id,omitempty"`
	NextTrackID     string `json:"next_track_id,omitempty"`
	ArtworkSHA256   string `json:"artwork_sha256,omitempty"`
	IsFavorite      *bool  `json:"is_favorite,omitempty"`
}
type playlistMutationBatch struct {
	Version          int                `json:"version"`
	ReconciliationID string             `json:"reconciliation_id"`
	Mutations        []playlistMutation `json:"mutations"`
}
type playlistMutationResult struct {
	MutationID string `json:"mutation_id"`
	Status     string `json:"status"`
}
type playlistMutationBatchResult struct {
	Version          int                      `json:"version"`
	ReconciliationID string                   `json:"reconciliation_id"`
	Results          []playlistMutationResult `json:"results"`
}

func validSHA256(v string) bool { _, err := hex.DecodeString(v); return err == nil && len(v) == 64 }

func (s *Service) applyPlaylistMutation(ctx context.Context, deviceID string, scope domain.MobileLibrarySyncScope, m playlistMutation, uploadedArtwork map[string]string) string {
	s.mutationMu.Lock()
	defer s.mutationMu.Unlock()
	if m.MutationID == "" || m.PlaylistID == "" || m.UpdatedAt <= 0 || !uuidLike(m.MutationID) {
		return "rejected"
	}
	if m.Operation == "SET_FAVORITE" {
		return s.applyFavoriteMutation(ctx, deviceID, m)
	}
	status := "rejected"
	apply := func(txCtx context.Context) error {
		if existing, err := s.ledger.Get(txCtx, deviceID, m.MutationID); err != nil {
			return err
		} else if existing != nil {
			status = "duplicate"
			return nil
		}
		if !playlistInScope(scope, m.PlaylistID) {
			status = "scope-conflict"
		} else {
			wins, err := s.lww.Claim(txCtx, m.PlaylistID, m.UpdatedAt, m.MutationID, m.Operation == "DELETE")
			if err != nil {
				return err
			}
			if !wins {
				status = "stale"
			} else {
				status = s.applyNewPlaylistMutation(txCtx, scope, m, uploadedArtwork)
			}
		}
		return s.ledger.Save(txCtx, domain.PlaylistMutationLedgerEntry{DeviceID: deviceID, MutationID: m.MutationID, Result: status, CreatedAt: time.Now().UTC()})
	}
	if s.tx == nil {
		if err := apply(ctx); err != nil {
			if s.logger != nil {
				s.logger.Warn("apply playlist mutation", "error", err)
			}
			return "rejected"
		}
		return status
	}
	if err := s.tx.RunInTx(ctx, apply); err != nil {
		if s.logger != nil {
			s.logger.Warn("apply playlist mutation transaction", "error", err)
		}
		return "rejected"
	}
	return status
}

func (s *Service) applyFavoriteMutation(ctx context.Context, deviceID string, m playlistMutation) string {
	if m.PlaylistID != playlistapp.FavoritesPlaylistID || m.Payload.TrackID == "" || m.Payload.IsFavorite == nil || s.favoriteLedger == nil || s.favoriteLWW == nil {
		return "rejected"
	}
	status := "rejected"
	apply := func(txCtx context.Context) error {
		if existing, err := s.favoriteLedger.Get(txCtx, deviceID, m.MutationID); err != nil {
			return err
		} else if existing != nil {
			status = "duplicate"
			return nil
		}
		track, err := s.tracks.GetByID(txCtx, m.Payload.TrackID)
		if err != nil {
			return err
		}
		if track == nil {
			status = "rejected"
		} else if track.UpdatedAt.UnixMilli() > m.UpdatedAt {
			// A desktop-side track update after the mobile edit is newer. This
			// preserves last-write-wins even before the desktop has a mobile
			// watermark for this track.
			status = "stale"
		} else {
			wins, err := s.favoriteLWW.Claim(txCtx, track.ID, m.UpdatedAt, m.MutationID, *m.Payload.IsFavorite)
			if err != nil {
				return err
			}
			if !wins {
				status = "stale"
			} else if err := s.tracks.SetFavorite(txCtx, track.ID, *m.Payload.IsFavorite); err != nil {
				return err
			} else {
				status = "applied"
			}
		}
		return s.favoriteLedger.Save(txCtx, domain.PlaylistMutationLedgerEntry{DeviceID: deviceID, MutationID: m.MutationID, Result: status, CreatedAt: time.Now().UTC()})
	}
	if s.tx != nil {
		if err := s.tx.RunInTx(ctx, apply); err != nil {
			return "rejected"
		}
	} else if err := apply(ctx); err != nil {
		return "rejected"
	}
	return status
}

func uuidLike(id string) bool { _, err := uuid.Parse(id); return err == nil }

func (s *Service) applyNewPlaylistMutation(ctx context.Context, scope domain.MobileLibrarySyncScope, m playlistMutation, uploadedArtwork map[string]string) string {
	if !playlistInScope(scope, m.PlaylistID) {
		return "scope-conflict"
	}
	p, err := s.playlists.GetByID(ctx, m.PlaylistID)
	if err != nil {
		return "rejected"
	}
	if p != nil && (p.IsSmart || (p.ID == playlistapp.FavoritesPlaylistID && m.Operation != "SET_ARTWORK" && m.Operation != "REMOVE_ARTWORK")) {
		return "rejected"
	}
	switch m.Operation {
	case "CREATE":
		if p != nil {
			return "duplicate"
		}
		if m.Payload.Name == "" {
			return "rejected"
		}
		if err := s.playlists.Save(ctx, &domain.Playlist{ID: m.PlaylistID, Name: m.Payload.Name, Description: m.Payload.Description}); err != nil {
			return "rejected"
		}
	case "UPDATE":
		if p == nil || m.Payload.Name == "" {
			return "rejected"
		}
		if err := s.playlistSvc.Update(ctx, m.PlaylistID, m.Payload.Name, m.Payload.Description); err != nil {
			return "rejected"
		}
	case "DELETE":
		if p == nil {
			return "duplicate"
		}
		if err := s.playlistSvc.Delete(ctx, m.PlaylistID); err != nil {
			return "rejected"
		}
	case "ADD_TRACK":
		if p == nil || m.Payload.TrackID == "" {
			return "rejected"
		}
		if err := s.playlistSvc.AddTrack(ctx, m.PlaylistID, m.Payload.TrackID); err != nil {
			return "rejected"
		}
	case "REMOVE_TRACK":
		if p == nil || m.Payload.TrackID == "" {
			return "rejected"
		}
		if err := s.playlistSvc.RemoveTrack(ctx, m.PlaylistID, m.Payload.TrackID); err != nil {
			return "rejected"
		}
	case "MOVE_TRACK":
		if p == nil || m.Payload.TrackID == "" {
			return "rejected"
		}
		if err := s.playlistSvc.MoveTrack(ctx, m.PlaylistID, m.Payload.TrackID, m.Payload.PreviousTrackID, m.Payload.NextTrackID); err != nil {
			return "rejected"
		}
	case "REMOVE_ARTWORK":
		if p == nil {
			return "rejected"
		}
		if err := s.playlistSvc.RemoveArtwork(ctx, m.PlaylistID); err != nil {
			return "rejected"
		}
	case "SET_ARTWORK":
		if p == nil || !validSHA256(m.Payload.ArtworkSHA256) {
			return "rejected"
		}
		key, uploaded := uploadedArtwork[m.Payload.ArtworkSHA256]
		if !uploaded {
			return "rejected"
		}
		p.ArtworkKey = &key
		if err := s.playlists.Update(ctx, p); err != nil {
			return "rejected"
		}
	default:
		return "rejected"
	}
	return "applied"
}

func playlistInScope(scope domain.MobileLibrarySyncScope, id string) bool {
	if scope.Kind == domain.MobileLibrarySyncScopeAll {
		return true
	}
	if scope.Kind != domain.MobileLibrarySyncScopePlaylists {
		return false
	}
	for _, selected := range scope.SelectedIDs {
		if selected == id {
			return true
		}
	}
	return false
}

func hashBody(body []byte) string { sum := sha256.Sum256(body); return fmt.Sprintf("%x", sum) }
