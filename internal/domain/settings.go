package domain

import (
	"fmt"
	"regexp"
	"strings"
)

// DefaultPrimaryColor is the application's original rose accent colour.
const DefaultPrimaryColor = "#E11D48"

var primaryColorPattern = regexp.MustCompile(`^#[0-9A-Fa-f]{6}$`)

// NormalizePrimaryColor validates a CSS hex colour and returns its canonical
// uppercase #RRGGBB representation.
func NormalizePrimaryColor(color string) (string, error) {
	color = strings.TrimSpace(color)
	if !primaryColorPattern.MatchString(color) {
		return "", fmt.Errorf("must be a #RRGGBB hex color")
	}
	return strings.ToUpper(color), nil
}
