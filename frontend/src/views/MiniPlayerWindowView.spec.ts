import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import MiniPlayerWindowView from './MiniPlayerWindowView.vue'

vi.mock('@wailsio/runtime', () => ({
  Events: {
    On: vi.fn(() => vi.fn()),
    Types: { Common: { WindowShow: 'window-show' } },
  },
  Create: {
    Nullable: (fn: (value: unknown) => unknown) => (value: unknown) => value == null ? null : fn(value),
    Array: (fn: (value: unknown) => unknown) => (values: unknown[]) => (values ?? []).map(fn),
    Struct: (ctor: new (value: unknown) => unknown) => (value: unknown) => value == null ? null : new ctor(value),
    Map: () => (value: unknown) => value,
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
}))

vi.mock('@/stores/player', () => ({
  usePlayerStore: () => ({ init: vi.fn(), syncState: vi.fn() }),
}))

vi.mock('@/stores/device', () => ({
  useDeviceStore: () => ({ init: vi.fn(), dispose: vi.fn() }),
}))

describe('MiniPlayerWindowView', () => {
  afterEach(() => {
    document.documentElement.style.backgroundColor = ''
    document.body.style.backgroundColor = ''
  })

  it('uses the active theme background for the document while mounted', () => {
    document.documentElement.style.backgroundColor = 'red'
    document.body.style.backgroundColor = 'red'
    const wrapper = mount(MiniPlayerWindowView, { global: { stubs: { MiniPlayerFloating: true } } })

    expect(document.documentElement.style.backgroundColor).toBe('var(--bg-main)')
    expect(document.body.style.backgroundColor).toBe('var(--bg-main)')

    wrapper.unmount()
    expect(document.documentElement.style.backgroundColor).toBe('red')
    expect(document.body.style.backgroundColor).toBe('red')
  })
})
