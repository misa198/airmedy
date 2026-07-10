import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SyncedLyricsView from './SyncedLyricsView.vue'

describe('SyncedLyricsView', () => {
  it('progressively blurs non-active lines in immersive mode', () => {
    const wrapper = mount(SyncedLyricsView, {
      props: {
        immersive: true,
        currentPosition: 15,
        lines: [
          { text: 'Zero', time: 0 },    // distance 3 → blur(2px),   opacity 0.1
          { text: 'First', time: 5 },   // distance 2 → blur(1.25px), opacity 0.15
          { text: 'Second', time: 10 }, // distance 1 → blur(0.35px), opacity 0.25
          { text: 'Active', time: 15 }, // distance 0 → blur(0),      opacity 1
          { text: 'Fourth', time: 20 }, // distance 1 → blur(0.35px), opacity 0.25
          { text: 'Fifth', time: 25 },  // distance 2 → blur(1.25px), opacity 0.15
        ],
      },
    })

    const lines = wrapper.findAll('[data-test="lyric-line"]')
    // blur
    expect(lines[3].attributes('style')).toContain('blur(0)')
    expect(lines[2].attributes('style')).toContain('blur(0.35px)')
    expect(lines[4].attributes('style')).toContain('blur(0.35px)')
    expect(lines[1].attributes('style')).toContain('blur(1.25px)')
    expect(lines[5].attributes('style')).toContain('blur(1.25px)')
    expect(lines[0].attributes('style')).toContain('blur(2px)')
    // opacity
    expect(lines[2].attributes('style')).toContain('opacity: 0.25')
    expect(lines[4].attributes('style')).toContain('opacity: 0.25')
    expect(lines[1].attributes('style')).toContain('opacity: 0.15')
    expect(lines[5].attributes('style')).toContain('opacity: 0.15')
    expect(lines[0].attributes('style')).toContain('opacity: 0.1')
  })
})

