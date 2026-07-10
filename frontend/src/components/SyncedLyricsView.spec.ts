import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SyncedLyricsView from './SyncedLyricsView.vue'

describe('SyncedLyricsView', () => {
  it('progressively blurs non-active lines in immersive mode', () => {
    const wrapper = mount(SyncedLyricsView, {
      props: {
        immersive: true,
        currentPosition: 10,
        lines: [
          { text: 'First', time: 0 },
          { text: 'Second', time: 5 },
          { text: 'Active', time: 10 },
          { text: 'Fourth', time: 15 },
          { text: 'Fifth', time: 20 },
        ],
      },
    })

    const lines = wrapper.findAll('[data-test="lyric-line"]')
    expect(lines[2].attributes('style')).toContain('blur(0)')
    expect(lines[1].attributes('style')).toContain('blur(0.35px)')
    expect(lines[0].attributes('style')).toContain('blur(1.25px)')
    expect(lines[4].attributes('style')).toContain('blur(1.25px)')
    expect(lines[1].attributes('style')).toContain('opacity: 0.25')
    expect(lines[0].attributes('style')).toContain('opacity: 0.15')
  })
})
