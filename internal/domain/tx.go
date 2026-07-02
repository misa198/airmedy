package domain

import "context"

// TxManager runs fn within a database transaction. Repository calls made
// with the ctx passed to fn transparently participate in that transaction.
// Reentrant: an outer RunInTx call wrapping an inner one does not open a
// second transaction.
type TxManager interface {
	RunInTx(ctx context.Context, fn func(ctx context.Context) error) error
}
