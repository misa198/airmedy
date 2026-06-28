package sqlite

import (
	"context"
	"database/sql"
	"fmt"

	"airmedy/internal/domain"
)

type miniPlayerStateRepository struct {
	db *DB
}

func NewMiniPlayerStateRepository(db *DB) domain.MiniPlayerStateRepository {
	return &miniPlayerStateRepository{db: db}
}

func (r *miniPlayerStateRepository) Save(ctx context.Context, state *domain.MiniPlayerState) error {
	_, err := r.db.ExecContext(ctx,
		`INSERT INTO mini_player_state (id, x, y, width, height, always_on_top, has_position, updated_at)
		 VALUES (1, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
		 ON CONFLICT(id) DO UPDATE SET
		   x = excluded.x,
		   y = excluded.y,
		   width = excluded.width,
		   height = excluded.height,
		   always_on_top = excluded.always_on_top,
		   has_position = excluded.has_position,
		   updated_at = excluded.updated_at`,
		state.X,
		state.Y,
		state.Width,
		state.Height,
		state.AlwaysOnTop,
		state.HasPosition,
	)
	if err != nil {
		return fmt.Errorf("failed to save mini player state: %w", err)
	}
	return nil
}

func (r *miniPlayerStateRepository) Load(ctx context.Context) (*domain.MiniPlayerState, error) {
	type row struct {
		X           int  `db:"x"`
		Y           int  `db:"y"`
		Width       int  `db:"width"`
		Height      int  `db:"height"`
		AlwaysOnTop bool `db:"always_on_top"`
		HasPosition bool `db:"has_position"`
	}
	var r2 row
	err := r.db.GetContext(ctx, &r2,
		`SELECT x, y, width, height, always_on_top, has_position
		 FROM mini_player_state WHERE id = 1`,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to load mini player state: %w", err)
	}

	return &domain.MiniPlayerState{
		X:           r2.X,
		Y:           r2.Y,
		Width:       r2.Width,
		Height:      r2.Height,
		AlwaysOnTop: r2.AlwaysOnTop,
		HasPosition: r2.HasPosition,
	}, nil
}
