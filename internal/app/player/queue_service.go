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
func (s *QueueService) SetQueue(tracks []*domain.TrackDTO, startIndex int) {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.originalList = tracks
	if s.shuffle {
		s.rebuildShuffle(startIndex)
	} else {
		s.currentIndex = startIndex
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

	s.shuffle = enabled
	if s.shuffle {
		s.rebuildShuffle(s.currentIndex)
	} else {
		// Restore original index
		if s.currentIndex >= 0 && s.currentIndex < len(s.shuffledList) {
			currentTrack := s.shuffledList[s.currentIndex]
			for i, t := range s.originalList {
				if t.ID == currentTrack.ID {
					s.currentIndex = i
					break
				}
			}
		}
	}
}

// SetRepeatMode updates the repeat mode.
func (s *QueueService) SetRepeatMode(mode domain.RepeatMode) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.repeatMode = mode
}

// GetQueue returns the current active list of tracks.
func (s *QueueService) GetQueue() []*domain.TrackDTO {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.activeList()
}

// Internal helpers

func (s *QueueService) activeList() []*domain.TrackDTO {
	if s.shuffle {
		return s.shuffledList
	}
	return s.originalList
}

func (s *QueueService) rebuildShuffle(keepIndex int) {
	if len(s.originalList) == 0 {
		s.shuffledList = nil
		s.currentIndex = -1
		return
	}

	// Create a copy of the original list to shuffle
	shuffled := make([]*domain.TrackDTO, len(s.originalList))
	copy(shuffled, s.originalList)

	var currentTrack *domain.TrackDTO
	if keepIndex >= 0 && keepIndex < len(s.originalList) {
		currentTrack = s.originalList[keepIndex]
		// Remove it from the list to shuffle
		shuffled = append(shuffled[:keepIndex], shuffled[keepIndex+1:]...)
	}

	// Fisher-Yates shuffle
	s.rng.Shuffle(len(shuffled), func(i, j int) {
		shuffled[i], shuffled[j] = shuffled[j], shuffled[i]
	})

	if currentTrack != nil {
		// Put the current track at the beginning
		s.shuffledList = append([]*domain.TrackDTO{currentTrack}, shuffled...)
		s.currentIndex = 0
	} else {
		s.shuffledList = shuffled
		s.currentIndex = 0
	}
}
