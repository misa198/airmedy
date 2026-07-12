package player

import (
	"log/slog"
	"math/rand"
	"testing"

	"airmedy/internal/domain"
)

func makeTrack(id string) *domain.TrackDTO {
	return &domain.TrackDTO{Track: domain.Track{ID: id, Title: id}}
}

func queueIDs(q *QueueService) []string {
	list := q.GetQueue()
	ids := make([]string, len(list))
	for i, t := range list {
		ids[i] = t.ID
	}
	return ids
}

func TestInsertAfterCurrent_EmptyQueue(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.InsertAfterCurrent(makeTrack("A"))
	ids := queueIDs(q)
	if len(ids) != 1 || ids[0] != "A" {
		t.Fatalf("expected [A], got %v", ids)
	}
}

func TestInsertAfterCurrent_AtHead(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 0)
	q.InsertAfterCurrent(makeTrack("X"))
	ids := queueIDs(q)
	expected := []string{"A", "X", "B", "C"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_AtMiddle(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 1)
	q.InsertAfterCurrent(makeTrack("X"))
	ids := queueIDs(q)
	expected := []string{"A", "B", "X", "C"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_AtEnd(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 2)
	q.InsertAfterCurrent(makeTrack("X"))
	ids := queueIDs(q)
	expected := []string{"A", "B", "C", "X"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_ShuffleInsertsAtCurrentPlusOne(t *testing.T) {
	q := NewQueueService(slog.Default())
	tracks := []*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}
	q.SetQueue(tracks, 0)
	q.SetShuffle(true)
	lenBefore := len(q.GetQueue())
	q.InsertAfterCurrent(makeTrack("X"))
	list := q.GetQueue()
	if len(list) != lenBefore+1 {
		t.Fatalf("expected length %d, got %d", lenBefore+1, len(list))
	}

	// Find where "A" (the track at original index 0) ended up
	aIdx := -1
	for i, tr := range list {
		if tr.ID == "A" {
			aIdx = i
			break
		}
	}
	if aIdx == -1 {
		t.Fatal("A not found in shuffled list")
	}

	// The inserted track must be after A
	if aIdx == len(list)-1 {
		t.Fatal("A is at end of list, X should have been inserted after it")
	}
	if list[aIdx+1].ID != "X" {
		t.Fatalf("expected X after A (index %d), got %s at index %d", aIdx, list[aIdx+1].ID, aIdx+1)
	}
}

func TestSetShuffle_OnlyShufflesTracksAfterCurrent(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.rng = rand.New(rand.NewSource(1))
	q.SetQueue([]*domain.TrackDTO{
		makeTrack("A"), makeTrack("B"), makeTrack("C"),
		makeTrack("D"), makeTrack("E"), makeTrack("F"),
	}, 2)

	q.SetShuffle(true)
	ids := queueIDs(q)
	if !equalSlices(ids[:3], []string{"A", "B", "C"}) {
		t.Fatalf("expected history and current track to remain [A B C], got %v", ids[:3])
	}
	if !sameTrackIDs(ids[3:], []string{"D", "E", "F"}) {
		t.Fatalf("expected shuffled suffix to contain [D E F], got %v", ids[3:])
	}
	if equalSlices(ids[3:], []string{"D", "E", "F"}) {
		t.Fatalf("expected deterministic shuffle to reorder upcoming tracks, got %v", ids[3:])
	}
	if q.currentIndex != 2 || q.GetCurrentTrack().ID != "C" {
		t.Fatalf("expected C to remain current at index 2, got %q at index %d", q.GetCurrentTrack().ID, q.currentIndex)
	}
}

func TestSetShuffle_AtLastTrackKeepsQueueOrder(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 2)

	q.SetShuffle(true)

	if ids := queueIDs(q); !equalSlices(ids, []string{"A", "B", "C"}) {
		t.Fatalf("expected no order change when no upcoming tracks exist, got %v", ids)
	}
}

func TestSetShuffle_DisablingRestoresOriginalOrderAndCurrentTrack(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.rng = rand.New(rand.NewSource(1))
	q.SetQueue([]*domain.TrackDTO{
		makeTrack("A"), makeTrack("B"), makeTrack("C"),
		makeTrack("D"), makeTrack("E"), makeTrack("F"),
	}, 2)
	q.SetShuffle(true)

	q.SetShuffle(false)

	if ids := queueIDs(q); !equalSlices(ids, []string{"A", "B", "C", "D", "E", "F"}) {
		t.Fatalf("expected original queue order, got %v", ids)
	}
	if q.currentIndex != 2 || q.GetCurrentTrack().ID != "C" {
		t.Fatalf("expected C to remain current at index 2, got %q at index %d", q.GetCurrentTrack().ID, q.currentIndex)
	}
}

func TestSetShuffle_WithoutCurrentTrackShufflesEntireQueue(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.rng = rand.New(rand.NewSource(1))
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, -1)

	q.SetShuffle(true)

	ids := queueIDs(q)
	if !sameTrackIDs(ids, []string{"A", "B", "C"}) {
		t.Fatalf("expected shuffled queue to contain [A B C], got %v", ids)
	}
	if equalSlices(ids, []string{"A", "B", "C"}) {
		t.Fatalf("expected deterministic full-queue shuffle to reorder tracks, got %v", ids)
	}
	if q.currentIndex != 0 {
		t.Fatalf("expected shuffled queue to select index 0, got %d", q.currentIndex)
	}
}

func TestShuffleTracks_DisablingRestoresSourceOrderAndCurrentTrack(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.rng = rand.New(rand.NewSource(1))
	source := []*domain.TrackDTO{
		makeTrack("A"), makeTrack("B"), makeTrack("C"),
		makeTrack("D"), makeTrack("E"), makeTrack("F"),
	}

	q.ShuffleTracks(source)
	currentID := q.GetCurrentTrack().ID
	if ids := queueIDs(q); equalSlices(ids, []string{"A", "B", "C", "D", "E", "F"}) {
		t.Fatalf("expected ShuffleTracks to reorder source queue, got %v", ids)
	}

	q.SetShuffle(false)

	if ids := queueIDs(q); !equalSlices(ids, []string{"A", "B", "C", "D", "E", "F"}) {
		t.Fatalf("expected source order after unshuffle, got %v", ids)
	}
	if track := q.GetCurrentTrack(); track == nil || track.ID != currentID {
		t.Fatalf("expected current track %q to remain selected, got %#v", currentID, track)
	}
}

func TestShuffleTracks_WithQueueCapUnshufflesSelectedTracksInSourceOrder(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.rng = rand.New(rand.NewSource(1))
	q.SetMaxSize(3)
	source := []*domain.TrackDTO{
		makeTrack("A"), makeTrack("B"), makeTrack("C"),
		makeTrack("D"), makeTrack("E"), makeTrack("F"),
	}

	q.ShuffleTracks(source)
	selected := queueIDs(q)
	currentID := q.GetCurrentTrack().ID

	q.SetShuffle(false)

	selectedSet := make(map[string]bool, len(selected))
	for _, id := range selected {
		selectedSet[id] = true
	}
	want := make([]string, 0, len(selected))
	for _, track := range source {
		if selectedSet[track.ID] {
			want = append(want, track.ID)
		}
	}
	if ids := queueIDs(q); !equalSlices(ids, want) {
		t.Fatalf("expected selected tracks in source order %v, got %v", want, ids)
	}
	if track := q.GetCurrentTrack(); track == nil || track.ID != currentID {
		t.Fatalf("expected current track %q to remain selected, got %#v", currentID, track)
	}
}

func TestMoodRadioRefill_UnshuffleRestoresSeedAndGeneratedOrder(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.rng = rand.New(rand.NewSource(1))
	seedBatch := []*domain.TrackDTO{makeTrack("seed"), makeTrack("one"), makeTrack("two")}
	refillBatch := []*domain.TrackDTO{makeTrack("three"), makeTrack("four")}

	q.ShuffleTracks(seedBatch)
	q.AppendTracks(refillBatch)
	currentID := q.GetCurrentTrack().ID

	q.SetShuffle(false)

	if ids := queueIDs(q); !equalSlices(ids, []string{"seed", "one", "two", "three", "four"}) {
		t.Fatalf("expected Mood Radio seed and refill order after unshuffle, got %v", ids)
	}
	if track := q.GetCurrentTrack(); track == nil || track.ID != currentID {
		t.Fatalf("expected current track %q to remain selected, got %#v", currentID, track)
	}
}

func TestInsertAfterCurrent_CurrentlyPlayingTrack_NoOp(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 1)
	q.InsertAfterCurrent(makeTrack("B")) // B is currently playing
	ids := queueIDs(q)
	expected := []string{"A", "B", "C"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v (no-op), got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_DuplicateAfterCurrent_MovesNext(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C"), makeTrack("D")}, 0)
	q.InsertAfterCurrent(makeTrack("C")) // C is after current (A)
	ids := queueIDs(q)
	expected := []string{"A", "C", "B", "D"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_DuplicateBeforeCurrent_MovesNext(t *testing.T) {
	q := NewQueueService(slog.Default())
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C"), makeTrack("D")}, 2)
	q.InsertAfterCurrent(makeTrack("A")) // A is before current (C)
	ids := queueIDs(q)
	expected := []string{"B", "C", "A", "D"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_DuplicateBeforeCurrent_Shuffle(t *testing.T) {
	q := NewQueueService(slog.Default())
	// Build a known shuffled order by setting queue then manually verifying via GetQueue
	tracks := []*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C"), makeTrack("D")}
	q.SetQueue(tracks, 0)
	q.SetShuffle(true)

	// Find where A is in the shuffled list
	list := q.GetQueue()
	aIdx := -1
	for i, trk := range list {
		if trk.ID == "A" {
			aIdx = i
			break
		}
	}
	if aIdx == -1 {
		t.Fatal("A not found in shuffled list")
	}
	// Force A to be the current track for the test
	q.currentIndex = aIdx

	// After shuffle, current track (A) is at aIdx; add a new track so we have something after current
	q.InsertAfterCurrent(makeTrack("X"))
	list = q.GetQueue()
	aIdx = -1 // Re-find A as it might have moved
	for i, trk := range list {
		if trk.ID == "A" {
			aIdx = i
			break
		}
	}

	if list[aIdx].ID != "A" {
		t.Fatalf("current track should be A, got %s", list[aIdx].ID)
	}
	if list[aIdx+1].ID != "X" {
		t.Fatalf("X should be after A, got %s", list[aIdx+1].ID)
	}
	lenBefore := len(list)

	// Now "play next" X again — X is already at aIdx+1, should stay there, no duplicate
	q.InsertAfterCurrent(makeTrack("X"))
	list2 := q.GetQueue()
	if len(list2) != lenBefore {
		t.Fatalf("expected length %d (no duplicate), got %d", lenBefore, len(list2))
	}
	if list2[aIdx+1].ID != "X" {
		t.Fatalf("X should still be after A, got %s", list2[aIdx+1].ID)
	}
}

func equalSlices(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func sameTrackIDs(got, want []string) bool {
	if len(got) != len(want) {
		return false
	}
	counts := make(map[string]int, len(got))
	for _, id := range got {
		counts[id]++
	}
	for _, id := range want {
		counts[id]--
		if counts[id] < 0 {
			return false
		}
	}
	return true
}
