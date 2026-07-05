import { emptyLimit, type SmartPlaylistConfig, type SmartPlaylistLimit } from './smartPlaylistFields'

// The Mood Playlist tab picks a rectangular region of energy x danceability
// space (both axes are [0,1], sigmoid-normalized by the backend's mood
// derivation) instead of building rules row-by-row like the tag-rule editor.
export interface MoodBox {
  energyMin: number
  energyMax: number
  danceMin: number
  danceMax: number
}

export function defaultMoodBox(): MoodBox {
  return { energyMin: 0.25, energyMax: 0.75, danceMin: 0.25, danceMax: 0.75 }
}

// limit/liveUpdating come from the dialog's shared Limit/Live-updating UI
// (also used by the Filters tab) — they must be threaded through here
// rather than hard-coded, otherwise whatever the user set (e.g. "cap to 25")
// is silently discarded when the Mood tab is the one submitted.
export function moodConfigFromBox(box: MoodBox, limit: SmartPlaylistLimit = emptyLimit(), liveUpdating = true): SmartPlaylistConfig {
  return {
    root: {
      match: 'all',
      rules: [
        { field: 'energy', op: 'between', value: [box.energyMin, box.energyMax] },
        { field: 'danceability', op: 'between', value: [box.danceMin, box.danceMax] },
      ],
      groups: [],
    },
    limit,
    live_updating: liveUpdating,
  }
}

// Inverse of moodConfigFromBox, for re-opening a mood playlist to edit it.
// Returns null if the stored config isn't exactly this shape (e.g. a user
// hand-edited it via the tag-rule editor into something else) — callers
// should fall back to a default box rather than guess at partial data.
export function boxFromMoodConfig(config: SmartPlaylistConfig | undefined | null): MoodBox | null {
  const rules = config?.root?.rules
  if (!rules || rules.length !== 2 || (config?.root?.groups?.length ?? 0) > 0) return null

  const energyRule = rules.find(r => r.field === 'energy' && r.op === 'between')
  const danceRule = rules.find(r => r.field === 'danceability' && r.op === 'between')
  if (!energyRule || !danceRule) return null

  const energy = energyRule.value
  const dance = danceRule.value
  if (!Array.isArray(energy) || energy.length !== 2 || !Array.isArray(dance) || dance.length !== 2) return null
  const [energyMin, energyMax] = energy
  const [danceMin, danceMax] = dance
  if (![energyMin, energyMax, danceMin, danceMax].every(n => typeof n === 'number' && Number.isFinite(n))) return null

  return { energyMin, energyMax, danceMin, danceMax }
}

export function isMoodBoxValid(box: MoodBox): boolean {
  return (
    box.energyMin < box.energyMax &&
    box.danceMin < box.danceMax &&
    box.energyMin >= 0 && box.energyMax <= 1 &&
    box.danceMin >= 0 && box.danceMax <= 1
  )
}
