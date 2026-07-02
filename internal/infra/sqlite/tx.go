package sqlite

import (
	"context"
	"database/sql"

	"airmedy/internal/domain"

	"github.com/jmoiron/sqlx"
)

type txCtxKey struct{}

func withTx(ctx context.Context, tx *sqlx.Tx) context.Context {
	return context.WithValue(ctx, txCtxKey{}, tx)
}

func txFromContext(ctx context.Context) (*sqlx.Tx, bool) {
	tx, ok := ctx.Value(txCtxKey{}).(*sqlx.Tx)
	return tx, ok
}

// sqlExecutor is the subset of *sqlx.DB / *sqlx.Tx methods repositories
// need. Both types implement it identically, so Ext can hand out either
// transparently.
type sqlExecutor interface {
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
	GetContext(ctx context.Context, dest any, query string, args ...any) error
	SelectContext(ctx context.Context, dest any, query string, args ...any) error
	NamedExecContext(ctx context.Context, query string, arg any) (sql.Result, error)
}

// Ext returns the ambient transaction carried on ctx if present, otherwise
// the plain db handle, so repository code can use this as a drop-in
// executor that transparently joins an outer RunTx when there is one.
func (db *DB) Ext(ctx context.Context) sqlExecutor {
	if tx, ok := txFromContext(ctx); ok {
		return tx
	}
	return db.DB
}

// RunTx runs fn inside a transaction, committing on success and rolling
// back on error. It is reentrant: if ctx already carries a transaction
// (e.g. an outer RunTx call), fn is invoked inline without opening a new
// one, since SQLite/database-sql has no nested transaction support on a
// single connection.
func (db *DB) RunTx(ctx context.Context, fn func(ctx context.Context) error) error {
	if _, ok := txFromContext(ctx); ok {
		return fn(ctx)
	}

	tx, err := db.BeginTxx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	if err := fn(withTx(ctx, tx)); err != nil {
		return err
	}
	return tx.Commit()
}

// txManager adapts DB.RunTx to the domain.TxManager interface.
type txManager struct {
	db *DB
}

func NewTxManager(db *DB) domain.TxManager {
	return &txManager{db: db}
}

func (m *txManager) RunInTx(ctx context.Context, fn func(ctx context.Context) error) error {
	return m.db.RunTx(ctx, fn)
}
