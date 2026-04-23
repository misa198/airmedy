//go:build production

package wails

// IsProduction is true in production.
const IsProduction = true

// GetFrontendURL returns the URL for the frontend.
// In production, this points to the internal asset handler.
func GetFrontendURL() string {
	return "/"
}
