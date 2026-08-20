package lyrics

import (
	"io"
	"log/slog"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestKugouSelectBestCandidate(t *testing.T) {
	provider := NewKugouProvider(slog.New(slog.NewTextHandler(io.Discard, nil)))
	candidates := []kugouCandidate{
		{ID: "wrong-title", Singer: "Adele", Song: "Someone Like You", Duration: 295_000, Score: 100},
		{ID: "wrong-duration", Singer: "Adele", Song: "Hello", Duration: 301_000, Score: 100},
		{ID: "lower-provider-score", Singer: "Adele", Song: "Hello", Duration: 294_000, Score: 40},
		{ID: "best", Singer: "Adele", Song: "Hello", Duration: 294_000, Score: 60},
	}

	best := provider.selectBestCandidate(candidates, "hello", "adele", 295)

	require.NotNil(t, best)
	require.Equal(t, "best", best.ID)
	require.Nil(t, provider.selectBestCandidate(candidates[:2], "hello", "adele", 295))
}
