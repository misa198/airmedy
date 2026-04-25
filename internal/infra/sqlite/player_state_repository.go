package sqlite

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"

	"airmedy/internal/domain"
)

type playerStateRepository struct {
	db *DB
}

func NewPlayerStateRepository(db *DB) domain.PlayerStateRepository {
	return &playerStateRepository{db: db}
}

func (r *playerStateRepository) Save(ctx context.Context, state *domain.PlayerState) error {
	ids, err := json.Marshal(state.QueueTrackIDs)
	if err != nil {
		return fmt.Errorf("failed to marshal queue track ids: %w", err)
	}
	_, err = r.db.ExecContext(ctx,
		`INSERT INTO player_state (id, queue_track_ids, current_track_id, position, volume, muted, shuffle, repeat_mode, updated_at)
		 VALUES (1, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   queue_track_ids = excluded.queue_track_ids,
		   current_track_id = excluded.current_track_id,
		   position = excluded.position,
		   volume = excluded.volume,
		   muted = excluded.muted,
		   shuffle = excluded.shuffle,
		   repeat_mode = excluded.repeat_mode,
		   updated_at = excluded.updated_at`,
		string(ids),
		state.CurrentTrackID,
		state.Position,
		state.Volume,
		state.Muted,
		state.Shuffle,
		string(state.RepeatMode),
	)
	if err != nil {
		return fmt.Errorf("failed to save player state: %w", err)
	}
	return nil
}

func (r *playerStateRepository) Load(ctx context.Context) (*domain.PlayerState, error) {
	type row struct {
		QueueTrackIDs  string         `db:"queue_track_ids"`
		CurrentTrackID sql.NullString `db:"current_track_id"`
		Position       float64        `db:"position"`
		Volume         float64        `db:"volume"`
		Muted          bool           `db:"muted"`
		Shuffle        bool           `db:"shuffle"`
		RepeatMode     string         `db:"repeat_mode"`
	}
	var r2 row
	err := r.db.GetContext(ctx, &r2,
		`SELECT queue_track_ids, current_track_id, position, volume, muted, shuffle, repeat_mode
		 FROM player_state WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load player state: %w", err)
	}

	var ids []string
	if err := json.Unmarshal([]byte(r2.QueueTrackIDs), &ids); err != nil {
		ids = nil
	}

	return &domain.PlayerState{
		QueueTrackIDs:  ids,
		CurrentTrackID: r2.CurrentTrackID.String,
		Position:       r2.Position,
		Volume:         r2.Volume,
		Muted:          r2.Muted,
		Shuffle:        r2.Shuffle,
		RepeatMode:     domain.RepeatMode(r2.RepeatMode),
	}, nil
}
