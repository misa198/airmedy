package sqlite

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"testing"
	"time"
)

func newTxTestDB(t *testing.T) *DB {
	t.Helper()
	dbPath := t.Name() + ".db"
	t.Cleanup(func() { _ = os.Remove(dbPath) })

	db, err := NewDB(dbPath, slog.Default())
	if err != nil {
		t.Fatalf("Failed to create test db: %v", err)
	}
	t.Cleanup(func() { _ = db.Close() })
	return db
}

func TestRunTxCommitsOnSuccess(t *testing.T) {
	db := newTxTestDB(t)
	ctx := context.Background()
	repo := NewTrackRepository(db)

	err := db.RunTx(ctx, func(ctx context.Context) error {
		_, execErr := db.Ext(ctx).ExecContext(ctx,
			"INSERT INTO tracks (id, path, title, sort_title, mtime) VALUES (?, ?, ?, ?, ?)",
			"tx-commit", "/tx/commit.mp3", "Commit", "Commit", time.Now())
		return execErr
	})
	if err != nil {
		t.Fatalf("RunTx returned error: %v", err)
	}

	got, err := repo.GetByID(ctx, "tx-commit")
	if err != nil {
		t.Fatalf("GetByID failed: %v", err)
	}
	if got == nil {
		t.Fatal("expected committed row to be visible after RunTx")
	}
}

func TestRunTxRollsBackOnError(t *testing.T) {
	db := newTxTestDB(t)
	ctx := context.Background()
	repo := NewTrackRepository(db)

	sentinel := errors.New("boom")
	err := db.RunTx(ctx, func(ctx context.Context) error {
		_, execErr := db.Ext(ctx).ExecContext(ctx,
			"INSERT INTO tracks (id, path, title, sort_title, mtime) VALUES (?, ?, ?, ?, ?)",
			"tx-rollback", "/tx/rollback.mp3", "Rollback", "Rollback", time.Now())
		if execErr != nil {
			return execErr
		}
		return sentinel
	})
	if !errors.Is(err, sentinel) {
		t.Fatalf("expected sentinel error, got %v", err)
	}

	got, err := repo.GetByID(ctx, "tx-rollback")
	if err != nil {
		t.Fatalf("GetByID failed: %v", err)
	}
	if got != nil {
		t.Fatal("expected rolled-back row to be absent")
	}
}

func TestRunTxReentrantDoesNotNest(t *testing.T) {
	db := newTxTestDB(t)
	ctx := context.Background()
	repo := NewTrackRepository(db)

	err := db.RunTx(ctx, func(ctx context.Context) error {
		outerTx, ok := txFromContext(ctx)
		if !ok {
			t.Fatal("expected outer RunTx to stash a tx in ctx")
		}

		return db.RunTx(ctx, func(ctx context.Context) error {
			innerTx, ok := txFromContext(ctx)
			if !ok {
				t.Fatal("expected inner RunTx to see a tx in ctx")
			}
			if innerTx != outerTx {
				t.Fatal("expected reentrant RunTx to reuse the outer transaction, not nest a new one")
			}
			_, execErr := db.Ext(ctx).ExecContext(ctx,
				"INSERT INTO tracks (id, path, title, sort_title, mtime) VALUES (?, ?, ?, ?, ?)",
				"tx-reentrant", "/tx/reentrant.mp3", "Reentrant", "Reentrant", time.Now())
			return execErr
		})
	})
	if err != nil {
		t.Fatalf("RunTx returned error: %v", err)
	}

	got, err := repo.GetByID(ctx, "tx-reentrant")
	if err != nil {
		t.Fatalf("GetByID failed: %v", err)
	}
	if got == nil {
		t.Fatal("expected reentrant-committed row to be visible")
	}
}

func TestExtReturnsTxOrDB(t *testing.T) {
	db := newTxTestDB(t)
	ctx := context.Background()

	if db.Ext(ctx) != sqlExecutor(db.DB) {
		t.Fatal("expected plain ctx to yield the db handle")
	}

	_ = db.RunTx(ctx, func(ctx context.Context) error {
		tx, ok := txFromContext(ctx)
		if !ok {
			t.Fatal("expected RunTx to stash a tx in ctx")
		}
		if db.Ext(ctx) != sqlExecutor(tx) {
			t.Fatal("expected Ext(ctx) to return the ambient tx")
		}
		return nil
	})
}
