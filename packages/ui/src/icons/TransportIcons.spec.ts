import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import BackwardFill from './BackwardFill.vue'
import ForwardFill from './ForwardFill.vue'
import PlayFill from './PlayFill.vue'
import PauseFill from './PauseFill.vue'

describe.each([BackwardFill, ForwardFill, PlayFill, PauseFill])('transport icon', (icon) => {
  it('uses current color and a 24px size by default', () => {
    const svg = mount(icon)
    expect(svg.attributes()).toMatchObject({ width: '24', height: '24', fill: 'currentColor', viewBox: icon === PlayFill || icon === PauseFill ? '4 4 48 48' : '0 0 56 56' })
  })

  it('accepts Lucide-style size and color props and CSS classes', () => {
    const svg = mount(icon, { props: { size: 32, color: 'rebeccapurple' }, attrs: { class: 'w-4 h-4' } })
    expect(svg.attributes()).toMatchObject({ width: '32', height: '32', fill: 'rebeccapurple' })
    expect(svg.classes()).toEqual(expect.arrayContaining(['w-4', 'h-4']))
  })
})
