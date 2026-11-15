import { expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PlainLyricsView from './PlainLyricsView.vue'

it('keeps primary rows mounted while toggling romanization and restores bilingual text', async () => {
  const wrapper = mount(PlainLyricsView, {
    props: { lines: [{ primary: '你好', secondary: 'Hello' }, { primary: '世界' }] },
    global: { stubs: { TransitionGroup: false } },
  })
  const rows = wrapper.findAll('.leading-relaxed').map(row => row.element)
  await wrapper.setProps({ secondary: ['nǐ hǎo', 'shì jiè'] })
  expect(wrapper.text()).toContain('nǐ hǎo')
  expect(wrapper.text()).not.toContain('Hello')
  expect(wrapper.findAll('.leading-relaxed').map(row => row.element)).toEqual(rows)
  await wrapper.setProps({ secondary: [] })
  expect(wrapper.text()).toContain('Hello')
  expect(wrapper.text()).not.toContain('shì jiè')
  expect(wrapper.findAll('.leading-relaxed').map(row => row.element)).toEqual(rows)
  wrapper.unmount()
})
