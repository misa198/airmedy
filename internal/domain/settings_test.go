package domain

import "testing"

func TestNormalizePrimaryColor(t *testing.T) {
	color, err := NormalizePrimaryColor(" #3b82f6 ")
	if err != nil {
		t.Fatalf("NormalizePrimaryColor returned error: %v", err)
	}
	if color != "#3B82F6" {
		t.Fatalf("NormalizePrimaryColor = %q, want #3B82F6", color)
	}

	for _, invalid := range []string{"", "3B82F6", "#FFF", "#GGGGGG", "#3B82F60"} {
		if _, err := NormalizePrimaryColor(invalid); err == nil {
			t.Errorf("NormalizePrimaryColor(%q) returned nil error", invalid)
		}
	}
}
