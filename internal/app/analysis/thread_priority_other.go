//go:build !windows && !linux

package analysis

// lowerCurrentThreadPriority is a no-op on platforms without a per-thread
// priority mechanism we want to use here. On darwin setpriority(2) applies to
// the whole process rather than a single thread, so lowering it would also
// deprioritize the UI and audio threads — not what we want; skip instead. The
// thermal/freeze problem this guards against is specific to weak Windows/Linux
// laptops anyway.
func lowerCurrentThreadPriority() {}
