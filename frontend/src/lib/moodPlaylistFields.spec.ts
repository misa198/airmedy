import { describe, expect, it } from 'vitest'
import { boxFromMoodConfig, isMoodBoxValid, moodConfigFromBox, type MoodBox } from './moodPlaylistFields'
import { emptyConfig } from './smartPlaylistFields'

describe('moodConfigFromBox / boxFromMoodConfig', () => {
  it('round-trips a box through a config', () => {
    const box: MoodBox = { energyMin: 0.2, energyMax: 0.8, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0.3, brightnessMax: 0.7 }
    const config = moodConfigFromBox(box)
    expect(boxFromMoodConfig(config)).toEqual(box)
  })

  it('omits unrestricted brightness with live_updating on', () => {
    const config = moodConfigFromBox({ energyMin: 0, energyMax: 1, danceMin: 0, danceMax: 1, brightnessMin: 0, brightnessMax: 1 })
    expect(config.root.match).toBe('all')
    expect(config.root.rules).toHaveLength(2)
    expect(config.root.groups).toHaveLength(0)
    expect(config.live_updating).toBe(true)
  })

  it('returns null for a config with no rules', () => {
    expect(boxFromMoodConfig(emptyConfig())).toBeNull()
  })

  it('returns null for a config with nested groups (tag-rule shape)', () => {
    const config = moodConfigFromBox({ energyMin: 0, energyMax: 1, danceMin: 0, danceMax: 1, brightnessMin: 0, brightnessMax: 1 })
    config.root.groups = [{ match: 'all', rules: [], groups: [] }]
    expect(boxFromMoodConfig(config)).toBeNull()
  })

  it('returns null when rules do not match the expected field/op shape', () => {
    const config = emptyConfig()
    config.root.rules = [
      { field: 'bpm', op: 'between', value: [90, 120] },
      { field: 'danceability', op: 'between', value: [0.1, 0.6] },
    ]
    expect(boxFromMoodConfig(config)).toBeNull()
  })

  it('returns null for malformed rule values', () => {
    const config = emptyConfig()
    config.root.rules = [
      { field: 'energy', op: 'between', value: [0.2] },
      { field: 'danceability', op: 'between', value: [0.1, 0.6] },
    ]
    expect(boxFromMoodConfig(config)).toBeNull()
  })

  it('adds and restores a constrained brightness rule', () => {
    const box: MoodBox = { energyMin: 0.2, energyMax: 0.8, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0.3, brightnessMax: 0.7 }
    const config = moodConfigFromBox(box)
    expect(config.root.rules).toHaveLength(3)
    expect(config.root.rules).toContainEqual({ field: 'brightness', op: 'between', value: [0.3, 0.7] })
    expect(boxFromMoodConfig(config)).toEqual(box)
  })

  it('reads legacy two-rule configs with unrestricted brightness', () => {
    const config = moodConfigFromBox({ energyMin: 0.2, energyMax: 0.8, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0, brightnessMax: 1 })
    expect(boxFromMoodConfig(config)).toMatchObject({ brightnessMin: 0, brightnessMax: 1 })
  })
})

describe('isMoodBoxValid', () => {
  it('accepts a well-formed box', () => {
    expect(isMoodBoxValid({ energyMin: 0.2, energyMax: 0.8, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0, brightnessMax: 1 })).toBe(true)
  })

  it('rejects an inverted range', () => {
    expect(isMoodBoxValid({ energyMin: 0.8, energyMax: 0.2, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0, brightnessMax: 1 })).toBe(false)
  })

  it('rejects a degenerate (zero-width) range', () => {
    expect(isMoodBoxValid({ energyMin: 0.5, energyMax: 0.5, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0, brightnessMax: 1 })).toBe(false)
  })

  it('rejects out-of-range bounds', () => {
    expect(isMoodBoxValid({ energyMin: -0.1, energyMax: 0.8, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0, brightnessMax: 1 })).toBe(false)
    expect(isMoodBoxValid({ energyMin: 0.2, energyMax: 1.1, danceMin: 0.1, danceMax: 0.6, brightnessMin: 0, brightnessMax: 1 })).toBe(false)
  })
})
