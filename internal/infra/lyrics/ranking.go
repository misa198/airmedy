package lyrics

import "math"

const (
	titleWeight     = 0.5
	artistWeight    = 0.3
	durationWeight  = 0.2
	maxDurationDiff = 5.0
	minTitleSim     = 0.7
)

func scoreCandidate(title, artist string, duration float64, wantTitle, wantArtist string, wantDuration int) float64 {
	titleSim := similarity(normalizeText(title), wantTitle)
	if titleSim < minTitleSim {
		return -1
	}
	durDiff := math.Abs(duration - float64(wantDuration))
	if durDiff > maxDurationDiff {
		return -1
	}
	artistSim := similarity(normalizeText(artist), wantArtist)
	durScore := 1.0 - (durDiff / maxDurationDiff)
	return titleSim*titleWeight + artistSim*artistWeight + durScore*durationWeight
}

func similarity(a, b string) float64 {
	if a == b {
		return 1.0
	}
	if len(a) == 0 || len(b) == 0 {
		return 0.0
	}
	ra, rb := []rune(a), []rune(b)
	la, lb := len(ra), len(rb)
	prev := make([]int, lb+1)
	for j := range prev {
		prev[j] = j
	}
	for i := 1; i <= la; i++ {
		curr := make([]int, lb+1)
		curr[0] = i
		for j := 1; j <= lb; j++ {
			if ra[i-1] == rb[j-1] {
				curr[j] = prev[j-1]
			} else {
				curr[j] = 1 + min3(prev[j], curr[j-1], prev[j-1])
			}
		}
		prev = curr
	}
	return 1.0 - float64(prev[lb])/float64(max(la, lb))
}

func min3(a, b, c int) int {
	if a < b {
		if a < c {
			return a
		}
		return c
	}
	if b < c {
		return b
	}
	return c
}
