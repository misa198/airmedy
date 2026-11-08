import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from './Modal.vue'

describe('Modal', () => {
  it('renders above page controls and floating menus', () => {
    const wrapper = mount(Modal, { props: { open: true }, attachTo: document.body })

    expect(document.body.querySelector('.modal-root')?.classList).toContain('z-[1004]')
    expect(document.body.querySelector('[data-test="modal-drag-region"]')?.getAttribute('style')).toContain('--wails-draggable: drag')
    wrapper.unmount()
  })
})
