import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { PlayFill, PauseFill } from '@airmedy/ui'
import PlayPauseCutout from './PlayPauseCutout.vue'

describe('PlayPauseCutout', () => {
  it('uses filled icons in a cutout mask for each playback state', async () => {
    const wrapper = mount(PlayPauseCutout, { props: { isPlaying: false } })
    expect(wrapper.findComponent(PlayFill).attributes('width')).toBe('16')
    expect(wrapper.findComponent(PlayFill).attributes('height')).toBe('16')
    expect(wrapper.find('circle').attributes('mask')).toContain(wrapper.find('mask').attributes('id'))
    await wrapper.setProps({ isPlaying: true })
    expect(wrapper.findComponent(PauseFill).attributes('width')).toBe('16')
    await wrapper.setProps({ iconSize: 96 / 7 })
    expect(wrapper.findComponent(PauseFill).attributes('width')).toBe(String(96 / 7))
  })
})
