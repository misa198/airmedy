import { describe, expect, it } from 'vitest'
import { DOMWrapper, mount } from '@vue/test-utils'
import ColorPicker from './ColorPicker.vue'

describe('ColorPicker', () => {
  it('emits a valid uppercase hex value and ignores invalid hex input', async () => {
    const wrapper = mount(ColorPicker, {
      attachTo: document.body,
      props: {
        modelValue: '#E11D48',
        ariaLabel: 'Custom color',
        hexLabel: 'Hex color',
      },
    })

    await wrapper.get('button[aria-label="Custom color"]').trigger('click')
    const element = document.body.querySelector<HTMLInputElement>('input[aria-label="Hex color"]')
    expect(element).not.toBeNull()
    const input = new DOMWrapper(element!)
    await input.setValue('#3b82f6')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['#3B82F6'])

    await input.setValue('#BAD')
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)

    wrapper.unmount()
  })
})
