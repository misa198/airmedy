import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TrackCard from './TrackCard.vue'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

const mockTrack = {
  id: 'track-1',
  title: 'Test Track',
  artists: [{ id: 'artist-1', name: 'Test Artist' }] as any,
  album: { id: 'album-1', title: 'Test Album' } as any,
  duration: 180,
  path: '/path/to/track',
  artwork_key: 'artwork-1'
} as any as TrackDTO

describe('TrackCard', () => {
  it('keeps the animated wrapper across track artwork, album artwork, and placeholder', async () => {
    const wrapper = mount(TrackCard, {
      props: { track: mockTrack },
      global: { mocks: { $t: (key: string) => key } },
    })
    const artwork = wrapper.get('.track-artwork').element

    for (const track of [
      { ...mockTrack, artwork_key: '', album: { ...mockTrack.album!, artwork_key: 'album-cover' } },
      { ...mockTrack, artwork_key: '', album: null },
      mockTrack,
    ]) {
      await wrapper.setProps({ track: track as TrackDTO })
      expect(wrapper.get('.track-artwork').element).toBe(artwork)
      expect(wrapper.find('img').exists()).toBe(Boolean(track.artwork_key || track.album?.artwork_key))
    }
  })

  it('emits contextmenu when right clicked', async () => {
    const wrapper = mount(TrackCard, {
      props: {
        track: mockTrack
      },
      global: {
        stubs: {
          LazyImg: true,
          Music: true,
          Play: true,
          User: true,
          Disc: true
        },
        mocks: {
          $t: (msg: string) => msg
        }
      }
    })

    await wrapper.trigger('contextmenu')
    expect(wrapper.emitted('contextmenu')).toBeTruthy()
    expect(wrapper.emitted('contextmenu')![0][1]).toEqual(mockTrack)
  })

  it('emits play when play button is clicked', async () => {
    const wrapper = mount(TrackCard, {
      props: {
        track: mockTrack
      },
      global: {
        stubs: {
          LazyImg: true,
          Music: true,
          Play: true,
          User: true,
          Disc: true
        },
        mocks: {
          $t: (msg: string) => msg
        }
      }
    })

    const playButton = wrapper.find('button')
    await playButton.trigger('click')
    expect(wrapper.emitted('play')).toBeTruthy()
    expect(wrapper.emitted('play')![0][0]).toEqual(mockTrack)
  })
})
