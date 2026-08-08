import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Badge from './Badge.vue'

describe('Badge', () => {
  it('uses its supplied color for the themed badge treatment', () => {
    const wrapper = mount(Badge, { props: { color: 'var(--text-muted)' }, slots: { default: 'Offline' } })

    expect(wrapper.text()).toBe('Offline')
    expect(wrapper.attributes('style')).toContain('--badge-color: var(--text-muted)')
    expect(wrapper.classes()).toContain('border')
  })
})
