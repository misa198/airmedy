package mood

import "testing"

var testPctl = Percentile{P1: 0, P5: 10, P50: 50, P95: 90, P99: 100}

func TestNormalize_MedianMapsToHalf(t *testing.T) {
	got := Normalize(50, testPctl, sigmoidSteepness)
	if diff := got - 0.5; diff < -1e-9 || diff > 1e-9 {
		t.Errorf("Normalize(median) = %v, want 0.5", got)
	}
}

func TestNormalize_ClampsAboveP99AndBelowP1(t *testing.T) {
	atP99 := Normalize(testPctl.P99, testPctl, sigmoidSteepness)
	aboveP99 := Normalize(1000, testPctl, sigmoidSteepness)
	if atP99 != aboveP99 {
		t.Errorf("Normalize should clamp above P99: got %v vs %v", atP99, aboveP99)
	}

	atP1 := Normalize(testPctl.P1, testPctl, sigmoidSteepness)
	belowP1 := Normalize(-1000, testPctl, sigmoidSteepness)
	if atP1 != belowP1 {
		t.Errorf("Normalize should clamp below P1: got %v vs %v", atP1, belowP1)
	}
}

func TestNormalize_Monotonic(t *testing.T) {
	xs := []float64{0, 10, 25, 50, 75, 90, 100}
	prev := -1.0
	for _, x := range xs {
		got := Normalize(x, testPctl, sigmoidSteepness)
		if got < prev {
			t.Errorf("Normalize not monotonic: x=%v got=%v prev=%v", x, got, prev)
		}
		prev = got
	}
}

func TestNormalize_DegenerateSpreadReturnsHalf(t *testing.T) {
	pctl := Percentile{P1: 5, P5: 5, P50: 5, P95: 5, P99: 5}
	got := Normalize(5, pctl, sigmoidSteepness)
	if got != 0.5 {
		t.Errorf("Normalize with degenerate spread = %v, want 0.5", got)
	}
}

func TestNormalize_SteepnessIncreasesSaturation(t *testing.T) {
	x := 75.0 // positive z relative to median
	low := Normalize(x, testPctl, 1.0)
	high := Normalize(x, testPctl, 5.0)
	if high <= low {
		t.Errorf("higher k should saturate further from 0.5: low=%v high=%v", low, high)
	}
}
