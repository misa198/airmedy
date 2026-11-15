import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, ref, toRef } from 'vue'
import { useLyricsLayoutMotion } from './useLyricsLayoutMotion'

const Host = defineComponent({
  props: { secondary: Boolean, following: Boolean },
  setup(props) {
    const viewport = ref<HTMLElement | null>(null)
    const rows = ref<HTMLElement[]>([])
    const stop = vi.fn()
    useLyricsLayoutMotion({
      secondary: toRef(props, 'secondary'), viewport, rows, activeIndex: ref(1),
      following: () => props.following, stop,
    })
    return { viewport, rows, stop }
  },
  template: '<div ref="viewport"><div v-for="i in 2" :key="i" ref="rows" class="row">Primary<span v-if="secondary">romanized</span></div></div>',
})

function create(following = true, reducedMotion = false) {
  vi.stubGlobal('matchMedia', () => ({ matches: reducedMotion }))
  const wrapper = mount(Host, { props: { secondary: true, following } })
  const viewport = wrapper.element as HTMLElement
  Object.defineProperties(viewport, { clientHeight: { value: 400 }, clientWidth: { value: 300 } })
  vi.spyOn(viewport, 'getBoundingClientRect').mockReturnValue({ top: 0, bottom: 400 } as DOMRect)
  const rows = wrapper.findAll('.row').map(row => row.element as HTMLElement)
  const animations: { cancel: ReturnType<typeof vi.fn>; onfinish?: () => void }[] = []
  const animate = vi.fn(() => {
    const animation = { cancel: vi.fn() }
    animations.push(animation)
    return animation as unknown as Animation
  })
  rows.forEach((row, index) => {
    row.animate = animate
    vi.spyOn(row, 'getBoundingClientRect').mockImplementation(() => {
      const top = (index === 0 ? (row.textContent?.includes('romanized') ? 100 : 80) : (row.textContent?.includes('romanized') ? 300 : 200)) - viewport.scrollTop
      return { top, bottom: top + 40 } as DOMRect
    })
  })
  viewport.scrollTop = 200
  return { wrapper, viewport, animate, animations }
}

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('lyrics secondary layout motion', () => {
  it('pins the active row before paint and only animates surrounding rows, including interrupted toggles', async () => {
    const { wrapper, viewport, animate, animations } = create()
    await wrapper.setProps({ secondary: false })
    await nextTick()
    expect(viewport.scrollTop).toBe(100)
    expect(wrapper.vm.stop).toHaveBeenCalledOnce()
    expect(animate).toHaveBeenCalledOnce()
    expect(animate).toHaveBeenCalledWith([{ translate: '0 -80px' }, { translate: '0 0' }], {
      duration: 320, easing: 'cubic-bezier(0.4, 0, 0.2, 1)',
    })
    await wrapper.setProps({ secondary: true })
    await nextTick()
    expect(viewport.scrollTop).toBe(200)
    expect(animations[0].cancel).toHaveBeenCalledOnce()
    wrapper.unmount()
    expect(animations[1].cancel).toHaveBeenCalledOnce()
  })

  it('preserves manual browsing while animating the changed rows', async () => {
    const { wrapper, viewport, animate } = create(false)
    await wrapper.setProps({ secondary: false })
    await nextTick()
    expect(viewport.scrollTop).toBe(200)
    expect(wrapper.vm.stop).not.toHaveBeenCalled()
    expect(animate).toHaveBeenCalledOnce()
    wrapper.unmount()
  })

  it('still pins the active row with reduced motion but skips animations', async () => {
    const { wrapper, viewport, animate } = create(true, true)
    await wrapper.setProps({ secondary: false })
    await nextTick()
    expect(viewport.scrollTop).toBe(100)
    expect(animate).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
