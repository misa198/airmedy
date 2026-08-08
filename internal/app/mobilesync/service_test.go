package mobilesync

import (
	"os"
	"path/filepath"
	"testing"

	"airmedy/internal/domain"
	"github.com/stretchr/testify/require"
)

func TestSameScopeIgnoresSelectionOrder(t *testing.T) {
	require.True(t, sameScope(
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeArtists, SelectedIDs: []string{"b", "a"}},
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeArtists, SelectedIDs: []string{"a", "b"}},
	))
	require.False(t, sameScope(
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeArtists, SelectedIDs: []string{"a"}},
		domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAlbums, SelectedIDs: []string{"a"}},
	))
}

func TestMarshalManifestProducesCompactWireBytes(t *testing.T) {
	manifest := domain.MobileLibrarySyncManifest{
		Version:  1,
		PlanID:   "plan-1",
		Revision: "a",
		Scope:    domain.MobileLibrarySyncScope{Kind: domain.MobileLibrarySyncScopeAll, SelectedIDs: []string{}},
		Lyrics:   map[string]*domain.Lyric{},
		Analysis: map[string]*domain.TrackFeatures{},
		Assets:   []domain.MobileLibrarySyncAsset{},
	}

	body, err := marshalManifest(manifest)
	require.NoError(t, err)
	require.NotEqual(t, byte('\n'), body[len(body)-1], "manifest wire bytes must not have Encoder's trailing newline")
}

func TestFileAssetIncludesStableContentHash(t *testing.T) {
	path := filepath.Join(t.TempDir(), "track.mp3")
	require.NoError(t, os.WriteFile(path, []byte("airmedy"), 0o600))
	asset, err := fileAsset("audio:track-1", "audio", path)
	require.NoError(t, err)
	require.Equal(t, "audio:track-1", asset.ID)
	require.Equal(t, int64(7), asset.Size)
	require.Equal(t, "0f86571fd055a92ddb32478158a63e87ee84883e1775acb56adbb5ef4bdbc5dc", asset.SHA256)
}
