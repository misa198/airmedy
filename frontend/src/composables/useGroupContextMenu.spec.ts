import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestingPinia } from '@pinia/testing'
import { setActivePinia } from 'pinia'
import { useGroupContextMenu } from './useGroupContextMenu'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  AppendTracks: vi.fn(),
  PlayNextTracks: vi.fn(),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/playlistservice', () => ({}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
  Create: {
    Nullable: (fn: any) => (value: any) => (value == null ? null : fn(value)),
    Array: (fn: any) => (value: any[]) => (value ?? []).map(fn),
    Struct: (ctor: any) => (value: any) => (value == null ? null : new ctor(value)),
    Map: () => (value: any) => value,
  },
}))

const tracks = [
  { id: 'track-1', title: 'Track 1' },
  { id: 'track-2', title: 'Track 2' },
] as TrackDTO[]

describe('useGroupContextMenu', () => {
  beforeEach(() => {
    setActivePinia(createTestingPinia({
      createSpy: vi.fn,
      initialState: { player: { queue: [] } },
    }))
    vi.clearAllMocks()
  })

  it('adds only tracks that are not already queued', () => {
    setActivePinia(createTestingPinia({
      createSpy: vi.fn,
      initialState: { player: { queue: [tracks[0]] } },
    }))

    const item = useGroupContextMenu().buildMenuItems(tracks)
      .find(menuItem => menuItem.label === 'context_menu.add_to_queue')

    expect(item?.disabled).toBeUndefined()
    item?.action?.()
    expect(PlayerService.AppendTracks).toHaveBeenCalledWith([tracks[1]])
  })

  it('keeps Add to Queue enabled when every track is already queued', () => {
    setActivePinia(createTestingPinia({
      createSpy: vi.fn,
      initialState: { player: { queue: tracks } },
    }))

    const item = useGroupContextMenu().buildMenuItems(tracks)
      .find(menuItem => menuItem.label === 'context_menu.add_to_queue')

    expect(item?.disabled).toBeUndefined()
    item?.action?.()
    expect(PlayerService.AppendTracks).not.toHaveBeenCalled()
  })
})
