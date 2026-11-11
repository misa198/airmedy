import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PlayPauseCutout from './PlayPauseCutout.vue'

describe('PlayPauseCutout', () => {
  it('uses Lucide icons in a cutout mask for each playback state', async () => {
    const wrapper = mount(PlayPauseCutout, { props: { isPlaying: false } })
    expect(wrapper.find('mask .lucide-play').attributes('width')).toBe('16')
    expect(wrapper.find('mask .lucide-play').attributes('height')).toBe('16')
    expect(wrapper.find('circle').attributes('mask')).toContain(wrapper.find('mask').attributes('id'))
    await wrapper.setProps({ isPlaying: true })
    expect(wrapper.find('mask .lucide-pause').attributes('width')).toBe('16')
    await wrapper.setProps({ iconSize: 96 / 7 })
    expect(wrapper.find('mask .lucide-pause').attributes('width')).toBe(String(96 / 7))
  })
})
