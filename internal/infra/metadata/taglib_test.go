package metadata

import (
	"testing"

	"airmedy/internal/domain"
)

func TestNormalizeSort(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{"The Beatles", "Beatles"},
		{"A Night at the Opera", "Night at the Opera"},
		{"An Awesome Wave", "Awesome Wave"},
		{"Normal Title", "Normal Title"},
	}

	for _, tc := range tests {
		got := domain.NormalizeSort(tc.input)
		if got != tc.expected {
			t.Errorf("NormalizeSort(%q) = %q, expected %q", tc.input, got, tc.expected)
		}
	}
}
