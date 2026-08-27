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

    expect(wrapper.findAll('button')[1].classes()).toEqual(expect.arrayContaining(['text-white', 'opacity-80']))
    expect(wrapper.findAll('button')[3].classes()).toEqual(expect.arrayContaining(['text-white', 'opacity-80']))
  })
})
