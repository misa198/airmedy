import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import TrackTable from './TrackTable.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(), Off: vi.fn() },
  Create: {
    Nullable: (fn: unknown) => (value: unknown) => value == null ? null : (fn as (input: unknown) => unknown)(value),
    Array: () => (value: unknown[]) => value ?? [],
    Struct: () => (value: unknown) => value,
    Map: () => (value: unknown) => value,
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
}))

class ResizeObserverMock {
  observe() {}
  disconnect() {}
}

vi.stubGlobal('ResizeObserver', ResizeObserverMock)

describe('TrackTable', () => {
  it('keeps the sticky header in sync when the header is horizontally scrolled', async () => {
    const wrapper = mount(TrackTable, {
      props: {
        tracks: [{ id: 'track-1' } as TrackDTO],
      },
      global: {
        plugins: [createTestingPinia({ createSpy: vi.fn })],
        mocks: { $t: (key: string) => key },
        stubs: {
          TrackContextMenu: true,
          TrackTableHeader: true,
          TrackTableRow: true,
        },
      },
    })

    const header = wrapper.get('[data-testid="track-table-header-scroll-container"]')
    const list = wrapper.get('.track-table-virtual-list')
    ;(header.element as HTMLElement).scrollLeft = 128
    await header.trigger('scroll')

    expect((list.element as HTMLElement).scrollLeft).toBe(128)
  })
})
