// Library rescan interval options. Single source of truth for the union type,
// the default, and the Select option list (rendered in a loop, not hardcoded
// per option). i18n label for each is `settings.sync.interval_${value}`.
//
// "15s" is a dev-only option for quickly exercising the periodic-sync path; it
// is a valid stored/settable value in any build (so a setting saved in dev
// still round-trips in prod), but VISIBLE_SYNC_INTERVALS hides it from the
// production UI.
export const SYNC_INTERVALS = ['15s', '15m', '30m', '1h', 'launch', 'manual'] as const

export type SyncInterval = (typeof SYNC_INTERVALS)[number]

export const DEFAULT_SYNC_INTERVAL: SyncInterval = '1h'

// VISIBLE_SYNC_INTERVALS is what the settings UI should render as options.
export const VISIBLE_SYNC_INTERVALS: readonly SyncInterval[] = import.meta.env.DEV
  ? SYNC_INTERVALS
  : SYNC_INTERVALS.filter((i) => i !== '15s')

// isSyncInterval narrows an arbitrary string to a known SyncInterval.
export function isSyncInterval(value: string): value is SyncInterval {
  return (SYNC_INTERVALS as readonly string[]).includes(value)
}
