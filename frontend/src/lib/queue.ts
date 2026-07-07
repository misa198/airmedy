// Max queue size options. Single source of truth for the union type, the
// default, and the Select option list (rendered in a loop, not hardcoded per
// option). Mirrors the SyncInterval pattern in librarySync.ts.
export const MAX_QUEUE_SIZE_OPTIONS = [100, 500, 1000, 2000, 3000] as const

export type MaxQueueSize = (typeof MAX_QUEUE_SIZE_OPTIONS)[number]

export const DEFAULT_MAX_QUEUE_SIZE: MaxQueueSize = 1000

// isMaxQueueSize narrows an arbitrary number to a known MaxQueueSize.
export function isMaxQueueSize(value: number): value is MaxQueueSize {
  return (MAX_QUEUE_SIZE_OPTIONS as readonly number[]).includes(value)
}
