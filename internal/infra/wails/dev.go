//go:build !production

package wails

// IsProduction is false in development.
const IsProduction = false

// GetFrontendURL returns the URL for the frontend.
// In development, this points to the Vite dev server.
func GetFrontendURL() string {
	return "http://localhost:9245"
}

func GetAppDataFolder() string {
	return ""
}
