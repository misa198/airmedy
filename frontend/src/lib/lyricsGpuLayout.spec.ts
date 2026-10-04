import { describe, expect, it } from 'vitest'
import { brightLayerVisible, fragmentFill, initialLyricAppearance, karaokeBaseAlpha, lyricAppearance, wordEmphasis, wordGlow, wordProgress } from './lyricsGpuLayout'

describe('GPU lyric effects', () => {
  it('lifts progressively across a word and holds after it ends', () => {
    const word = { text: 'Hello', start: 10, end: 11 }
    expect([9, 10, 10.25, 10.5, 11, 12].map(position =>
      wordEmphasis(word, position),
    )).toEqual([0, 0, 0.5, 1, 1, 1])
    // The middle is partly lifted while the beginning is up and the end is still down.
    expect(wordEmphasis(word, 10.5, 0, 100)).toBe(1)
    expect(wordEmphasis(word, 10.5, 50, 100)).toBeCloseTo(0.5)
    expect(wordEmphasis(word, 10.5, 80, 100)).toBe(0)
    expect(wordEmphasis(word, 12, 100, 100)).toBe(1)
    expect(wordEmphasis(word, 10, 80, 100)).toBe(0) // Backward seek resets.
    expect(wordEmphasis({ ...word, end: 10 }, 10)).toBe(0)
    expect(wordEmphasis(word, 10.5, 0, 0)).toBe(0)
    const short = { ...word, end: 10.1 }
    expect(wordEmphasis(short, 10.1)).toBeCloseTo(1 / 3.5)
    expect(wordEmphasis(short, 12)).toBeCloseTo(1 / 3.5)
    expect(wordGlow(word, 11.25)).toBeCloseTo(0.5)
    expect(wordGlow(word, 11.5)).toBe(0)
    for (const immersive of [false, true]) {
      expect(lyricAppearance(0, 0, false, immersive, -1, true).scale).toBe(1)
      expect(lyricAppearance(0, 0, false, immersive, -1).scale).toBeGreaterThan(1)
    }
  })
  it('preserves immersive distance effects and makes browse text fully readable', () => {
    expect([0, 1, 2, 3].map(index => lyricAppearance(index, 0, false, true, -1))).toEqual([
      { blur: 0, alpha: 1, scale: 1.06 },
      { blur: 0.35, alpha: 0.25, scale: 1 },
      { blur: 1.25, alpha: 0.15, scale: 1 },
      { blur: 2, alpha: 0.1, scale: 1 },
    ])
    expect(lyricAppearance(9, 0, true, true, -1)).toEqual({ blur: 0, alpha: 1, scale: 1 })
    expect(lyricAppearance(1, 0, false, false, 1)).toEqual({ blur: 0, alpha: 0.24, scale: 1 })
    expect(initialLyricAppearance(lyricAppearance(0, 0, false, true, -1))).toEqual({ blur: 0, alpha: 1, scale: 1 })
  })
  it('sweeps a timed phrase across wrapped fragments and responds to seeks', () => {
    const word = { text: 'A phrase that wraps', start: 10, end: 12 }
    const progress = wordProgress(word, 11)
    expect(fragmentFill(progress, 0, 60, 100)).toBe(50)
    expect(fragmentFill(progress, 60, 40, 100)).toBe(0)
    expect(fragmentFill(wordProgress(word, 11.5), 60, 40, 100)).toBe(15)
    expect(wordProgress(word, 9)).toBe(0)
    expect(wordProgress(word, 13)).toBe(1)
    expect(wordProgress({ ...word, end: 10 }, 10)).toBe(1)
    expect(wordProgress({ ...word, end: 10 }, 9)).toBe(0)
  })
  it('keeps unsung text from dimming while an adjacent row becomes active', () => {
    expect([0.25, 0.5, 1].map(alpha => alpha * karaokeBaseAlpha(alpha))).toEqual([0.25, 0.35, 0.35])
    expect(brightLayerVisible(false, 100)).toBe(false)
    expect(brightLayerVisible(true, 100)).toBe(true)
  })
})
