// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RangeSlider from './RangeSlider.vue'

describe('RangeSlider', () => {
  it('emits an ordered range when either thumb moves', async () => {
    const wrapper = mount(RangeSlider, { props: { modelValue: [0.2, 0.8], min: 0, max: 1, step: 0.01 } })
    const [low, high] = wrapper.findAll('input')

    await low.setValue('0.9')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([[0.8, 0.8]])

    await high.setValue('0.1')
    expect(wrapper.emitted('update:modelValue')?.[1]).toEqual([[0.2, 0.2]])
  })
})
