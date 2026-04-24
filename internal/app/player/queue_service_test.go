package player

import (
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
	q := NewQueueService()
	q.InsertAfterCurrent(makeTrack("A"))
	ids := queueIDs(q)
	if len(ids) != 1 || ids[0] != "A" {
		t.Fatalf("expected [A], got %v", ids)
	}
}

func TestInsertAfterCurrent_AtHead(t *testing.T) {
	q := NewQueueService()
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 0)
	q.InsertAfterCurrent(makeTrack("X"))
	ids := queueIDs(q)
	expected := []string{"A", "X", "B", "C"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_AtMiddle(t *testing.T) {
	q := NewQueueService()
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 1)
	q.InsertAfterCurrent(makeTrack("X"))
	ids := queueIDs(q)
	expected := []string{"A", "B", "X", "C"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_AtEnd(t *testing.T) {
	q := NewQueueService()
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 2)
	q.InsertAfterCurrent(makeTrack("X"))
	ids := queueIDs(q)
	expected := []string{"A", "B", "C", "X"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_ShuffleInsertsAtCurrentPlusOne(t *testing.T) {
	q := NewQueueService()
	tracks := []*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}
	q.SetQueue(tracks, 0)
	q.SetShuffle(true)
	lenBefore := len(q.GetQueue())
	q.InsertAfterCurrent(makeTrack("X"))
	list := q.GetQueue()
	if len(list) != lenBefore+1 {
		t.Fatalf("expected length %d, got %d", lenBefore+1, len(list))
	}
	// The inserted track must be at index 1 (after current at 0)
	if list[1].ID != "X" {
		t.Fatalf("expected X at index 1, got %s", list[1].ID)
	}
}

func TestInsertAfterCurrent_CurrentlyPlayingTrack_NoOp(t *testing.T) {
	q := NewQueueService()
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C")}, 1)
	q.InsertAfterCurrent(makeTrack("B")) // B is currently playing
	ids := queueIDs(q)
	expected := []string{"A", "B", "C"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v (no-op), got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_DuplicateAfterCurrent_MovesNext(t *testing.T) {
	q := NewQueueService()
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C"), makeTrack("D")}, 0)
	q.InsertAfterCurrent(makeTrack("C")) // C is after current (A)
	ids := queueIDs(q)
	expected := []string{"A", "C", "B", "D"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_DuplicateBeforeCurrent_MovesNext(t *testing.T) {
	q := NewQueueService()
	q.SetQueue([]*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C"), makeTrack("D")}, 2)
	q.InsertAfterCurrent(makeTrack("A")) // A is before current (C)
	ids := queueIDs(q)
	expected := []string{"B", "C", "A", "D"}
	if !equalSlices(ids, expected) {
		t.Fatalf("expected %v, got %v", expected, ids)
	}
}

func TestInsertAfterCurrent_DuplicateBeforeCurrent_Shuffle(t *testing.T) {
	q := NewQueueService()
	// Build a known shuffled order by setting queue then manually verifying via GetQueue
	tracks := []*domain.TrackDTO{makeTrack("A"), makeTrack("B"), makeTrack("C"), makeTrack("D")}
	q.SetQueue(tracks, 0)
	q.SetShuffle(true)
	// After shuffle, current track (A) is at index 0; add a new track so we have something after current
	q.InsertAfterCurrent(makeTrack("X"))
	list := q.GetQueue()
	if list[0].ID != "A" {
		t.Fatalf("current track should be A at index 0, got %s", list[0].ID)
	}
	if list[1].ID != "X" {
		t.Fatalf("X should be at index 1 after insert, got %s", list[1].ID)
	}
	lenBefore := len(list)

	// Now "play next" X again — X is already at index 1, should stay at index 1, no duplicate
	q.InsertAfterCurrent(makeTrack("X"))
	list2 := q.GetQueue()
	if len(list2) != lenBefore {
		t.Fatalf("expected length %d (no duplicate), got %d", lenBefore, len(list2))
	}
	if list2[1].ID != "X" {
		t.Fatalf("X should still be at index 1, got %s", list2[1].ID)
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
