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
