package player

import (
	"math/rand"
	"sync"
	"time"

	"airmedy/internal/domain"
)

// QueueService manages the playback queue, including shuffling and repeat modes.
type QueueService struct {
	mu           sync.RWMutex
	originalList []*domain.TrackDTO
	shuffledList []*domain.TrackDTO
	currentIndex int // Index in the active list (shuffled or original)
	repeatMode   domain.RepeatMode
	shuffle      bool
	rng          *rand.Rand
}

func NewQueueService() *QueueService {
	return &QueueService{
		currentIndex: -1,
		repeatMode:   domain.RepeatModeOff,
		rng:          rand.New(rand.NewSource(time.Now().UnixNano())),
	}
}

// SetQueue replaces the entire queue and sets the current track index.
// Shuffle is always reset to false — use ShuffleTracks to start with shuffle enabled.
func (s *QueueService) SetQueue(tracks []*domain.TrackDTO, startIndex int) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.shuffle = false
	s.shuffledList = nil
	s.originalList = tracks
	s.currentIndex = startIndex
}

// ShuffleTracks replaces the queue, enables shuffle, and shuffles all tracks.
func (s *QueueService) ShuffleTracks(tracks []*domain.TrackDTO) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.originalList = tracks
	s.shuffle = true

	if len(tracks) == 0 {
		s.shuffledList = nil
		s.currentIndex = -1
		return
	}

	shuffled := make([]*domain.TrackDTO, len(tracks))
	copy(shuffled, tracks)
	s.rng.Shuffle(len(shuffled), func(i, j int) {
		shuffled[i], shuffled[j] = shuffled[j], shuffled[i]
	})

	s.shuffledList = shuffled
	s.currentIndex = 0
}

// SetCurrentIndex moves the active queue pointer to the given index without modifying the queue.
func (s *QueueService) SetCurrentIndex(index int) {
	s.mu.Lock()
	defer s.mu.Unlock()
	list := s.activeList()
	if index >= 0 && index < len(list) {
		s.currentIndex = index
	}
}

// GetCurrentTrack returns the track currently at the head of the queue.
func (s *QueueService) GetCurrentTrack() *domain.TrackDTO {
	s.mu.RLock()
	defer s.mu.RUnlock()

	list := s.activeList()
	if s.currentIndex >= 0 && s.currentIndex < len(list) {
		return list[s.currentIndex]
	}
	return nil
}

// PeekNext returns the next track in the queue without moving the index.
func (s *QueueService) PeekNext() *domain.TrackDTO {
	s.mu.RLock()
	defer s.mu.RUnlock()

	list := s.activeList()
	if len(list) == 0 {
		return nil
	}

	if s.repeatMode == domain.RepeatModeOne {
		if s.currentIndex >= 0 && s.currentIndex < len(list) {
			return list[s.currentIndex]
		}
	}

	nextIndex := s.currentIndex + 1
	if nextIndex >= len(list) {
		if s.repeatMode == domain.RepeatModeAll {
			nextIndex = 0
		} else {
			return nil
		}
	}

	if nextIndex >= 0 && nextIndex < len(list) {
		return list[nextIndex]
	}
	return nil
}

// PeekPrevious returns the previous track in the queue without moving the index.
func (s *QueueService) PeekPrevious() *domain.TrackDTO {
	s.mu.RLock()
	defer s.mu.RUnlock()

	list := s.activeList()
	if len(list) == 0 {
		return nil
	}

	if s.repeatMode == domain.RepeatModeOne {
		if s.currentIndex >= 0 && s.currentIndex < len(list) {
			return list[s.currentIndex]
		}
	}

	prevIndex := s.currentIndex - 1
	if prevIndex < 0 {
		if s.repeatMode == domain.RepeatModeAll {
			prevIndex = len(list) - 1
		} else {
			return nil
		}
	}

	if prevIndex >= 0 && prevIndex < len(list) {
		return list[prevIndex]
	}
	return nil
}

// Next moves to the next track based on repeat and shuffle settings.
// Returns nil if there are no more tracks to play.
func (s *QueueService) Next() *domain.TrackDTO {
	s.mu.Lock()
	defer s.mu.Unlock()

	list := s.activeList()
	if len(list) == 0 {
		return nil
	}

	if s.repeatMode == domain.RepeatModeOne {
		// Stay on current track
		if s.currentIndex < 0 {
			s.currentIndex = 0
		}
	} else {
		s.currentIndex++
		if s.currentIndex >= len(list) {
			if s.repeatMode == domain.RepeatModeAll {
				s.currentIndex = 0
			} else {
				s.currentIndex = len(list) // Mark as finished
				return nil
			}
		}
	}

	return list[s.currentIndex]
}

// Previous moves to the previous track.
func (s *QueueService) Previous() *domain.TrackDTO {
	s.mu.Lock()
	defer s.mu.Unlock()

	list := s.activeList()
	if len(list) == 0 {
		return nil
	}

	s.currentIndex--
	if s.currentIndex < 0 {
		if s.repeatMode == domain.RepeatModeAll {
			s.currentIndex = len(list) - 1
		} else {
			s.currentIndex = 0 // Stay at start
		}
	}

	return list[s.currentIndex]
}

// InsertAfterCurrent inserts a track immediately after the current position.
// If the track is currently playing, it is a no-op.
// If the track is already in the queue, it is moved to the next position instead of duplicated.
func (s *QueueService) InsertAfterCurrent(track *domain.TrackDTO) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.insertAfterCurrentLocked(track)
}

func (s *QueueService) insertAfterCurrentLocked(track *domain.TrackDTO) {
	list := s.activeList()

	// Currently playing — no-op.
	if s.currentIndex >= 0 && s.currentIndex < len(list) && list[s.currentIndex].ID == track.ID {
		return
	}

	// Already in queue — remove it first, then re-insert after current.
	existingIdx := -1
	for i, t := range list {
		if i != s.currentIndex && t.ID == track.ID {
			existingIdx = i
			break
		}
	}

	if existingIdx >= 0 {
		if s.shuffle {
			s.shuffledList = sliceRemove(s.shuffledList, existingIdx)
			if existingIdx < s.currentIndex {
				s.currentIndex--
			}
			for i, t := range s.originalList {
				if t.ID == track.ID {
					s.originalList = sliceRemove(s.originalList, i)
					break
				}
			}
		} else {
			s.originalList = sliceRemove(s.originalList, existingIdx)
			if existingIdx < s.currentIndex {
				s.currentIndex--
			}
		}
	}

	insertAt := s.currentIndex + 1
	if insertAt > len(s.originalList) {
		insertAt = len(s.originalList)
	}
	s.originalList = sliceInsert(s.originalList, insertAt, track)

	if s.shuffle {
		si := s.currentIndex + 1
		if si > len(s.shuffledList) {
			si = len(s.shuffledList)
		}
		s.shuffledList = sliceInsert(s.shuffledList, si, track)
	}
}

// InsertListAfterCurrent inserts a list of tracks immediately after the current position.
func (s *QueueService) InsertListAfterCurrent(tracks []*domain.TrackDTO) {
	s.mu.Lock()
	defer s.mu.Unlock()

	// Insert in reverse order using the locked version to maintain correct final order
	// and keep implementation simple while avoiding repeated locking.
	for i := len(tracks) - 1; i >= 0; i-- {
		s.insertAfterCurrentLocked(tracks[i])
	}
}

func sliceRemove(list []*domain.TrackDTO, at int) []*domain.TrackDTO {
	out := make([]*domain.TrackDTO, len(list)-1)
	copy(out, list[:at])
	copy(out[at:], list[at+1:])
	return out
}

func sliceInsert(list []*domain.TrackDTO, at int, t *domain.TrackDTO) []*domain.TrackDTO {
	out := make([]*domain.TrackDTO, len(list)+1)
	copy(out, list[:at])
	out[at] = t
	copy(out[at+1:], list[at:])
	return out
}

// SetShuffle enables or disables shuffling.
func (s *QueueService) SetShuffle(enabled bool) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.shuffle == enabled {
		return
	}

	if enabled {
		// Enabling shuffle: build shuffled list and find current track's new index
		var currentID string
		if s.currentIndex >= 0 && s.currentIndex < len(s.originalList) {
			currentID = s.originalList[s.currentIndex].ID
		}

		s.shuffle = true
		s.rebuildShuffle(-1, false)

		if currentID != "" {
			for i, t := range s.shuffledList {
				if t.ID == currentID {
					s.currentIndex = i
					break
				}
			}
		}
	} else {
		// Disabling shuffle: restore original index
		if s.currentIndex >= 0 && s.currentIndex < len(s.shuffledList) {
			currentID := s.shuffledList[s.currentIndex].ID
			s.shuffle = false
			for i, t := range s.originalList {
				if t.ID == currentID {
					s.currentIndex = i
					break
				}
			}
		} else {
			s.shuffle = false
		}
	}
}

// SetRepeatMode updates the repeat mode.
func (s *QueueService) SetRepeatMode(mode domain.RepeatMode) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.repeatMode = mode
}

// GetRepeatMode returns the current repeat mode.
func (s *QueueService) GetRepeatMode() domain.RepeatMode {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.repeatMode
}

// GetShuffle returns the current shuffle state.
func (s *QueueService) GetShuffle() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.shuffle
}

// GetQueue returns the current active list of tracks.
func (s *QueueService) GetQueue() []*domain.TrackDTO {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.activeList()
}

// GetOriginalQueue returns the original (unshuffled) list of tracks.
func (s *QueueService) GetOriginalQueue() []*domain.TrackDTO {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.originalList
}

// Restore sets both the original and shuffled lists directly.
func (s *QueueService) Restore(original, shuffled []*domain.TrackDTO, currentIndex int, shuffle bool, repeatMode domain.RepeatMode) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.originalList = original
	s.shuffledList = shuffled
	s.currentIndex = currentIndex
	s.shuffle = shuffle
	s.repeatMode = repeatMode
}

// IsEmpty returns true if the queue has no tracks.
func (s *QueueService) IsEmpty() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return len(s.activeList()) == 0
}

// UpdateTrack updates the metadata of a track if it exists in the queue.
func (s *QueueService) UpdateTrack(track *domain.TrackDTO) {
	s.mu.Lock()
	defer s.mu.Unlock()

	for _, t := range s.originalList {
		if t.ID == track.ID {
			t.IsFavorite = track.IsFavorite
		}
	}
	for _, t := range s.shuffledList {
		if t.ID == track.ID {
			t.IsFavorite = track.IsFavorite
		}
	}
}

// RemoveTrack removes a track from the queue by its ID.
func (s *QueueService) RemoveTrack(trackID string) {
	s.mu.Lock()
	defer s.mu.Unlock()

	// Find in original list
	originalIdx := -1
	for i, t := range s.originalList {
		if t.ID == trackID {
			originalIdx = i
			break
		}
	}

	if originalIdx == -1 {
		return
	}

	// Determine if we are removing the current track
	list := s.activeList()
	isRemovingCurrent := s.currentIndex >= 0 && s.currentIndex < len(list) && list[s.currentIndex].ID == trackID

	if s.shuffle {
		// Find in shuffled list
		shuffledIdx := -1
		for i, t := range s.shuffledList {
			if t.ID == trackID {
				shuffledIdx = i
				break
			}
		}

		if shuffledIdx >= 0 {
			s.shuffledList = sliceRemove(s.shuffledList, shuffledIdx)
			if shuffledIdx < s.currentIndex {
				s.currentIndex--
			}
		}
		s.originalList = sliceRemove(s.originalList, originalIdx)
	} else {
		s.originalList = sliceRemove(s.originalList, originalIdx)
		if originalIdx < s.currentIndex {
			s.currentIndex--
		}
	}

	// If we removed the current track, we might need to adjust the index
	// so GetCurrentTrack() returns the next logical track.
	if isRemovingCurrent {
		newList := s.activeList()
		if len(newList) == 0 {
			s.currentIndex = -1
		} else if s.currentIndex >= len(newList) {
			if s.repeatMode == domain.RepeatModeAll {
				s.currentIndex = 0
			} else {
				s.currentIndex = len(newList) // Mark as finished
			}
		}
	}
}

// ReorderQueue sets a new order for the active list using track IDs and maintains the current track index.
func (s *QueueService) ReorderQueue(trackIDs []string) {
	s.mu.Lock()
	defer s.mu.Unlock()

	activeList := s.activeList()
	trackMap := make(map[string]*domain.TrackDTO)
	for _, t := range activeList {
		trackMap[t.ID] = t
	}

	newActiveList := make([]*domain.TrackDTO, 0, len(trackIDs))
	for _, id := range trackIDs {
		if t, ok := trackMap[id]; ok {
			newActiveList = append(newActiveList, t)
		}
	}

	// Update the appropriate list
	if s.shuffle {
		s.shuffledList = newActiveList
	} else {
		s.originalList = newActiveList
	}

	// Maintain current index by finding the ID of the track that was at the old index
	if s.currentIndex >= 0 && s.currentIndex < len(activeList) {
		currentID := activeList[s.currentIndex].ID
		for i, t := range newActiveList {
			if t.ID == currentID {
				s.currentIndex = i
				break
			}
		}
	}
}

// Internal helpers

func (s *QueueService) activeList() []*domain.TrackDTO {
	if s.shuffle {
		return s.shuffledList
	}
	return s.originalList
}

func (s *QueueService) rebuildShuffle(keepIndex int, pickRandom bool) {
	if len(s.originalList) == 0 {
		s.shuffledList = nil
		s.currentIndex = -1
		return
	}

	shuffled := make([]*domain.TrackDTO, len(s.originalList))
	copy(shuffled, s.originalList)

	s.rng.Shuffle(len(shuffled), func(i, j int) {
		shuffled[i], shuffled[j] = shuffled[j], shuffled[i]
	})

	s.shuffledList = shuffled
	if s.currentIndex < 0 {
		s.currentIndex = 0
	}
}
