import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createTestingPinia } from '@pinia/testing'
import { setActivePinia } from 'pinia'
import { useTrackContextMenu } from './useTrackContextMenu'
import { usePlayerStore } from '@/stores/player'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

// Mock i18n
vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

// Mock router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

// Mock bindings
vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  PlayNext: vi.fn(),
  GetStatus: vi.fn().mockResolvedValue({}),
  GetQueue: vi.fn().mockResolvedValue([]),
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/playlistservice', () => ({
  GetPlaylistsForTrack: vi.fn().mockResolvedValue([]),
}))
vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({}))
vi.mock('../../bindings/airmedy/internal/infra/wails/lyricsservice', () => ({}))

// Mock @wailsio/runtime for store init and model creation
vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(), Off: vi.fn() },
  Create: {
    Nullable: (fn: any) => (v: any) => (v == null ? null : fn(v)),
    Array: (fn: any) => (arr: any[]) => (arr ?? []).map(fn),
    Struct: (ctor: any) => (v: any) => (v == null ? null : new ctor(v)),
    Map: (k: any, v: any) => (val: any) => val,
  },
}))

describe('useTrackContextMenu', () => {
  beforeEach(() => {
    setActivePinia(createTestingPinia({
      createSpy: vi.fn,
      stubActions: false,
    }))
  })

  it('excludes "Play Next" if track is currently playing', () => {
    const track: TrackDTO = { id: 'track-1', title: 'Track 1' } as any
    const playerStore = usePlayerStore()
    playerStore.currentTrack = track

    const { buildMenuItems } = useTrackContextMenu(vi.fn())
    
    const items = buildMenuItems(track)
    const playNextItem = items.find(item => item.label === 'context_menu.play_next')
    
    expect(playNextItem).toBeUndefined()
  })

  it('includes "Play Next" if track is not currently playing', () => {
    const track: TrackDTO = { id: 'track-1', title: 'Track 1' } as any
    const otherTrack: TrackDTO = { id: 'track-2', title: 'Track 2' } as any
    const playerStore = usePlayerStore()
    playerStore.currentTrack = otherTrack

    const { buildMenuItems } = useTrackContextMenu(vi.fn())
    
    const items = buildMenuItems(track)
    const playNextItem = items.find(item => item.label === 'context_menu.play_next')
    
    expect(playNextItem).toBeDefined()
    expect(playNextItem?.label).toBe('context_menu.play_next')
  })

  it('excludes "Play Next" if excludePlayNext option is true', () => {
    const track: TrackDTO = { id: 'track-1', title: 'Track 1' } as any
    const otherTrack: TrackDTO = { id: 'track-2', title: 'Track 2' } as any
    const playerStore = usePlayerStore()
    playerStore.currentTrack = otherTrack

    const { buildMenuItems } = useTrackContextMenu(vi.fn())
    
    const items = buildMenuItems(track, { excludePlayNext: true })
    const playNextItem = items.find(item => item.label === 'context_menu.play_next')
    
    expect(playNextItem).toBeUndefined()
  })

  it('excludes "Remove from Queue" if track is currently playing', () => {
    const track: TrackDTO = { id: 'track-1', title: 'Track 1' } as any
    const playerStore = usePlayerStore()
    playerStore.currentTrack = track

    const { buildMenuItems } = useTrackContextMenu(vi.fn())
    
    const items = buildMenuItems(track, { showRemoveFromQueue: true })
    const removeItem = items.find(item => item.label === 'context_menu.remove_from_queue')
    
    expect(removeItem).toBeUndefined()
  })

  it('includes "Remove from Queue" if track is not currently playing', () => {
    const track: TrackDTO = { id: 'track-1', title: 'Track 1' } as any
    const otherTrack: TrackDTO = { id: 'track-2', title: 'Track 2' } as any
    const playerStore = usePlayerStore()
    playerStore.currentTrack = otherTrack

    const { buildMenuItems } = useTrackContextMenu(vi.fn())
    
    const items = buildMenuItems(track, { showRemoveFromQueue: true })
    const removeItem = items.find(item => item.label === 'context_menu.remove_from_queue')
    
    expect(removeItem).toBeDefined()
    expect(removeItem?.label).toBe('context_menu.remove_from_queue')
  })
})
