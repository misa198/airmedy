//go:build !darwin && !linux && !windows

package wails

// buildIfaceKindMap returns an empty map on non-macOS platforms.
// classifyInterface falls back to name-based heuristics.
func buildIfaceKindMap() map[string]string {
	return map[string]string{}
}
