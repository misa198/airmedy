import { defineComponent, h, KeepAlive, nextTick, ref } from 'vue'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useLibrarySync } from './useLibrarySync'

enableAutoUnmount(afterEach)

const handlers = vi.hoisted(() => new Map<string, (event: { data?: unknown }) => void>())

vi.mock('@wailsio/runtime', () => ({
  Events: {
    On: vi.fn((name: string, handler: (event: { data?: unknown }) => void) => {
      handlers.set(name, handler)
      const off = vi.fn(() => handlers.delete(name))
      return off
    }),
  },
}))

describe('useLibrarySync', () => {
  const reload = vi.fn()
  const show = ref(true)
  const version = ref(0)
  const relevant = ref(false)

  const Probe = defineComponent({
    setup() {
      useLibrarySync(reload, { favorites: { version: () => version.value, relevant: () => relevant.value } })
      return () => h('div')
    },
  })

  const Host = defineComponent({
    setup: () => () => h(KeepAlive, null, [show.value ? h(Probe) : null]),
  })

  beforeEach(() => {
    vi.useFakeTimers()
    handlers.clear()
    reload.mockReset()
    show.value = true
    version.value = 0
    relevant.value = false
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('coalesces visible-view updates into one reload', async () => {
    mount(Host)
    await nextTick()

    handlers.get('library:track-updated')?.({})
    handlers.get('library:updated')?.({})
    await vi.advanceTimersByTimeAsync(50)

    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('defers updates received while cached until the view is activated', async () => {
    mount(Host)
    await nextTick()
    show.value = false
    await nextTick()

    handlers.get('library:track-updated')?.({})
    await vi.advanceTimersByTimeAsync(50)
    expect(reload).not.toHaveBeenCalled()

    show.value = true
    await nextTick()
    await vi.advanceTimersByTimeAsync(50)

    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('reloads after a completed mobile sync', async () => {
    mount(Host)
    await nextTick()

    handlers.get('mobile-library-sync:updated')?.({ data: { status: 'complete' } })
    await vi.advanceTimersByTimeAsync(50)

    expect(reload).toHaveBeenCalledTimes(1)
  })
  it('ignores unrelated favorites and coalesces related changes', async () => {
    const wrapper = mount(Host)
    version.value++
    await vi.advanceTimersByTimeAsync(50)
    expect(reload).not.toHaveBeenCalled()
    relevant.value = true
    version.value++
    await nextTick()
    version.value++
    await vi.advanceTimersByTimeAsync(50)
    expect(reload).toHaveBeenCalledTimes(1)
    expect(reload).toHaveBeenCalledWith('favorite')
    wrapper.unmount()
  })

  it('defers cached favorites and gives metadata refresh priority', async () => {
    const wrapper = mount(Host)
    relevant.value = true
    show.value = false
    await nextTick()
    version.value++
    await vi.advanceTimersByTimeAsync(50)
    expect(reload).not.toHaveBeenCalled()
    handlers.get('library:track-updated')?.({})
    show.value = true
    await nextTick()
    await vi.advanceTimersByTimeAsync(50)
    expect(reload).toHaveBeenCalledTimes(1)
    expect(reload).toHaveBeenCalledWith('library')
    wrapper.unmount()
  })

  it('serializes in-flight reloads and coalesces their follow-up', async () => {
    let finish!: () => void
    reload.mockImplementationOnce(() => new Promise<void>(resolve => { finish = resolve }))
    const wrapper = mount(Host)
    relevant.value = true
    version.value++
    await vi.advanceTimersByTimeAsync(50)
    version.value++
    await nextTick()
    version.value++
    await vi.advanceTimersByTimeAsync(100)
    expect(reload).toHaveBeenCalledTimes(1)
    finish()
    await vi.advanceTimersByTimeAsync(50)
    expect(reload).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('cancels pending refresh on unmount', async () => {
    const wrapper = mount(Host)
    relevant.value = true
    version.value++
    await nextTick()
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(50)
    expect(reload).not.toHaveBeenCalled()
  })

})
