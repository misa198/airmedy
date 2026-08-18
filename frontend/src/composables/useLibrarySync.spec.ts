import { defineComponent, h, KeepAlive, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useLibrarySync } from './useLibrarySync'

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

  const Probe = defineComponent({
    setup() {
      useLibrarySync(reload)
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
})
