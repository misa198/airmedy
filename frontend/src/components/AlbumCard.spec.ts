import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { AlbumDTO } from '../../bindings/airmedy/internal/domain/models'
import AlbumCard from './AlbumCard.vue'

describe('AlbumCard', () => {
  it('keeps the animated artwork wrapper when artwork loads, changes, or disappears', async () => {
    const album = new AlbumDTO({ id: 'album-1', title: 'Album' })
    const wrapper = mount(AlbumCard, {
      props: { album },
      global: { mocks: { $t: (key: string) => key } },
    })
    const artwork = wrapper.get('.album-artwork').element

    for (const artwork_key of ['cover-1', 'cover-2', '']) {
      await wrapper.setProps({ album: new AlbumDTO({ ...album, artwork_key }) })
      expect(wrapper.get('.album-artwork').element).toBe(artwork)
      expect(wrapper.find('img').exists()).toBe(Boolean(artwork_key))
      if (artwork_key) expect(wrapper.get('img').classes()).not.toContain('group-hover:scale-105')
    }
  })
})
