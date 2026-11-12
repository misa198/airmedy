import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { RepeatMode } from '../../../bindings/airmedy/internal/domain/models'
import PlayerPlaybackControls from './PlayerPlaybackControls.vue'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

describe('PlayerPlaybackControls', () => {
  it('applies transparency to transport buttons instead of their icon color', () => {
    const wrapper = mount(PlayerPlaybackControls, {
      props: {
        isPlaying: false,
        shuffle: false,
        repeatMode: RepeatMode.RepeatModeOff,
        showIndicator: false,
      },
    })

    expect(wrapper.findAll('button')[1].classes()).toEqual(expect.arrayContaining(['text-mini-player-pill-foreground', 'opacity-80']))
    expect(wrapper.findAll('button')[3].classes()).toEqual(expect.arrayContaining(['text-mini-player-pill-foreground', 'opacity-80']))
  })

  it('disables all controls when the tray has no track', () => {
    const wrapper = mount(PlayerPlaybackControls, {
      props: { isPlaying: false, shuffle: false, repeatMode: RepeatMode.RepeatModeOff, showIndicator: true, disabled: true },
    })
    expect(wrapper.findAll('button')).toHaveLength(5)
    expect(wrapper.findAll('button').every(button => button.attributes('disabled') !== undefined)).toBe(true)
  })

  it('uses smaller transport buttons in compact mode', () => {
    const wrapper = mount(PlayerPlaybackControls, {
      props: { isPlaying: false, shuffle: true, repeatMode: RepeatMode.RepeatModeAll, showIndicator: true, compact: true },
    })
    expect(wrapper.classes()).toContain('gap-6')
    expect(wrapper.findAll('button')[2].classes()).toEqual(expect.arrayContaining(['w-9', 'h-9', 'scale-100', 'hover:scale-105', 'transition-transform', 'duration-300']))
    expect(wrapper.findAll('button')[1].get('svg').classes()).toEqual(expect.arrayContaining(['w-7', 'h-7']))
  })

  it('does not scale the play/pause button when requested', () => {
    const wrapper = mount(PlayerPlaybackControls, {
      props: { isPlaying: false, shuffle: false, repeatMode: RepeatMode.RepeatModeOff, showIndicator: false, disablePlayPauseScale: true },
    })

    expect(wrapper.findAll('button')[2].classes()).not.toContain('hover:scale-105')
  })
})
