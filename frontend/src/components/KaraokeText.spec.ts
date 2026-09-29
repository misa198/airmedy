import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import KaraokeText from './KaraokeText.vue'

describe('KaraokeText', () => {
  const line = { text: 'Hello world', time: 1, words: [
    { text: 'Hello ', start: 1, end: 2 }, { text: 'world', start: 2, end: 3 },
  ] }
  it('keeps the same word nodes and whitespace when entering and leaving the active line', async () => {
    const wrapper = mount(KaraokeText, { props: { line } })
    const root = wrapper.element
    const words = Array.from(root.children)
    expect(words).toHaveLength(2)
    for (const position of [1, 2.5, undefined, 1.5, undefined]) {
      await wrapper.setProps({ position })
      expect(wrapper.element).toBe(root)
      words.forEach((word, index) => expect(wrapper.element.children[index]).toBe(word))
      expect(wrapper.text()).toBe('Hello world')
    }
    wrapper.unmount()
  })
  it('applies the unsung opacity immediately when a line becomes active', async () => {
    const wrapper = mount(KaraokeText, { props: { line } })
    await wrapper.setProps({ position: 1 })
    expect([...document.head.querySelectorAll('style')].some(style => style.textContent?.includes('transition: --karaoke-unsung-opacity'))).toBe(false)
    wrapper.unmount()
  })
  it('sweeps within words, holds without playback updates, and seeks in both directions', async () => {
    const wrapper = mount(KaraokeText, { props: { line, position: 1.5 } })
    const fills = () => wrapper.findAll('.karaoke-word').map(el => (el.element as HTMLElement).style.getPropertyValue('--word-progress'))
    expect(fills()).toEqual(['50%', '0%'])
    expect(wrapper.text()).toBe('Hello world')
    await wrapper.setProps({ position: 1.5 })
    expect(fills()).toEqual(['50%', '0%'])
    await wrapper.setProps({ position: 1.75 })
    expect(fills()).toEqual(['75%', '0%'])
    await wrapper.setProps({ position: 2.5 })
    expect(fills()).toEqual(['100%', '50%'])
    await wrapper.setProps({ position: 0 })
    expect(fills()).toEqual(['0%', '0%'])
    await wrapper.setProps({ position: 10 })
    expect(fills()).toEqual(['100%', '100%'])
    await wrapper.setProps({ position: undefined })
    expect(wrapper.find('.karaoke-word').exists()).toBe(false)
    expect(wrapper.text()).toBe('Hello world')
    wrapper.unmount()
  })

  it('handles zero-duration words and resets on replacement with ordinary lyrics', async () => {
    const wrapper = mount(KaraokeText, { props: {
      position: 1, line: { text: 'A', time: 1, words: [{ text: 'A', start: 1, end: 1 }] },
    } })
    expect(wrapper.get('.karaoke-word').attributes('style')).toContain('100%')
    await wrapper.setProps({ line: { text: 'New song', time: 0 } })
    expect(wrapper.find('.karaoke-word').exists()).toBe(false)
    expect(wrapper.text()).toBe('New song')
    wrapper.unmount()
  })
})
