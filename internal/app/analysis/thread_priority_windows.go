//go:build windows

package analysis

import "golang.org/x/sys/windows"

// THREAD_PRIORITY_BELOW_NORMAL lowers a thread one step below the process's
// base priority, so the scheduler favours interactive work (UI, audio) over
// this background decode without starving it entirely.
const threadPriorityBelowNormal = -1

var (
	kernel32             = windows.NewLazySystemDLL("kernel32.dll")
	procSetThreadPrio    = kernel32.NewProc("SetThreadPriority")
	procGetCurrentThread = kernel32.NewProc("GetCurrentThread")
)

// lowerCurrentThreadPriority drops the calling OS thread's scheduling priority.
// The caller must have pinned the goroutine via runtime.LockOSThread first so
// the change applies to the goroutine's own thread and isn't undone by the Go
// scheduler migrating it. Best-effort: a failure is non-fatal (analysis just
// runs at normal priority), so the result is intentionally ignored.
func lowerCurrentThreadPriority() {
	prio := int32(threadPriorityBelowNormal)
	h, _, _ := procGetCurrentThread.Call() // pseudo-handle for the calling thread
	_, _, _ = procSetThreadPrio.Call(h, uintptr(uint32(prio)))
}
