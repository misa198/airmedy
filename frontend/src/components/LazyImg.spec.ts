import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LazyImg from './LazyImg.vue'

describe('LazyImg', () => {
  it('retries one failed request without disabling lazy loading', async () => {
    const wrapper = mount(LazyImg, { props: { src: '/artwork/one.jpg?size=md' } })
    const image = wrapper.get('img')

    expect(image.attributes('loading')).toBe('lazy')
    await image.trigger('error')
    expect(image.attributes('src')).toBe('/artwork/one.jpg?size=md&retry=1')

    await image.trigger('error')
    expect(image.attributes('src')).toBe('/artwork/one.jpg?size=md&retry=1')

    await wrapper.setProps({ src: '/artwork/two.jpg' })
    expect(image.attributes('src')).toBe('/artwork/two.jpg')
  })
})
