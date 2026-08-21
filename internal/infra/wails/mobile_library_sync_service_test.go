package wails

import (
	"testing"

	"airmedy/internal/domain"
	"github.com/stretchr/testify/require"
)

func TestSyncPlanStatusOmitsManifest(t *testing.T) {
	plan := &domain.MobileLibrarySyncPlan{Manifest: domain.MobileLibrarySyncManifest{Assets: []domain.MobileLibrarySyncAsset{{ID: "audio:track"}}}}

	status := syncPlanStatus(plan)

	require.Empty(t, status.Manifest.Assets)
	require.Len(t, plan.Manifest.Assets, 1)
}
