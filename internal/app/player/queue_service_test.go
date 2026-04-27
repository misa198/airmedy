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
