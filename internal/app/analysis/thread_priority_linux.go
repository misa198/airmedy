//go:build linux

package analysis

import "golang.org/x/sys/unix"

// analysisNiceness is the nice value applied to each analysis worker thread.
// On Linux nice is per-thread (each pthread is a schedulable task), so this
// only deprioritizes the decode work, not the whole process. +10 is a clear
// step down without fully starving background progress.
const analysisNiceness = 10

// lowerCurrentThreadPriority raises the calling thread's niceness so the CPU-
// heavy ffmpeg/aubio decode yields to interactive work. who=0 targets the
// calling thread (a task) on Linux; the caller pins the goroutine via
// runtime.LockOSThread first. Best-effort: failure is non-fatal and ignored.
func lowerCurrentThreadPriority() {
	_ = unix.Setpriority(unix.PRIO_PROCESS, 0, analysisNiceness)
}
