package lyrics

import (
	"html"
	"testing"
)

func TestHTMLEntitiesUnescaping(t *testing.T) {
	tests := []struct {
		input    string
		expected string
	}{
		{
			input:    "don&apos;t",
			expected: "don't",
		},
		{
			input:    "d&apos;accord",
			expected: "d'accord",
		},
		{
			input:    "A &amp; B",
			expected: "A & B",
		},
		{
			input:    "&quot;Hello&quot;",
			expected: `"Hello"`,
		},
		{
			input:    "&#39;test&#39;",
			expected: "'test'",
		},
	}

	for _, tc := range tests {
		t.Run(tc.input, func(t *testing.T) {
			got := html.UnescapeString(tc.input)
			if got != tc.expected {
				t.Errorf("expected %q, got %q", tc.expected, got)
			}
		})
	}
}
