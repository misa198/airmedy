import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ColorPicker from './ColorPicker.vue'

describe('ColorPicker', () => {
  it('emits a valid uppercase hex value and ignores invalid hex input', async () => {
    const wrapper = mount(ColorPicker, {
      props: {
        modelValue: '#E11D48',
        ariaLabel: 'Custom color',
        hexLabel: 'Hex color',
      },
    })

    await wrapper.get('button[aria-label="Custom color"]').trigger('click')
    const input = wrapper.get('input[aria-label="Hex color"]')
    await input.setValue('#3b82f6')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['#3B82F6'])

    await input.setValue('#BAD')
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
  })
})
