import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { compileStyle, parse } from 'vue/compiler-sfc'
import { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import PlaylistCard from './PlaylistCard.vue'
import source from './PlaylistCard.vue?raw'
import trackCardSource from './TrackCard.vue?raw'

describe('PlaylistCard artwork', () => {
  it('keeps the animated wrapper across placeholder, single cover, mosaic, and custom cover', async () => {
    const playlist = new Playlist({ id: 'playlist-1', name: 'Playlist' })
    const wrapper = mount(PlaylistCard, {
      props: { playlist },
      global: { mocks: { $t: (key: string) => key } },
    })
    const artwork = wrapper.get('.playlist-artwork').element
    const tracks = Array.from({ length: 4 }, (_, i) => new TrackDTO({ id: `track-${i}`, artwork_key: `cover-${i}` }))

    for (const state of [
      { playlist, tracks: tracks.slice(0, 1), images: 1 },
      { playlist, tracks, images: 4 },
      { playlist: new Playlist({ ...playlist, artwork_key: 'custom-cover' }), tracks, images: 1 },
      { playlist, tracks: [], images: 0 },
    ]) {
      await wrapper.setProps({ playlist: state.playlist, tracks: state.tracks })
      expect(wrapper.get('.playlist-artwork').element).toBe(artwork)
      expect(wrapper.findAll('img')).toHaveLength(state.images)
    }
    wrapper.unmount()
  })

  it('uses the same scoped hover animation as TrackCard', () => {
    const compile = (component: string) => {
      const { descriptor } = parse(component)
      return compileStyle({
        source: descriptor.styles[0]?.content ?? '',
        filename: 'Artwork.vue', id: 'data-v-test', scoped: true,
      }).code
    }
    expect(compile(source)).toContain(compile(trackCardSource).replaceAll('track-artwork', 'playlist-artwork').trim())
  })
})
