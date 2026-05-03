import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import MetadataEditDialog from './MetadataEditDialog.vue'
import { MetadataUpdate, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { createTestI18n, setupTestPinia } from '../lib/test-utils'

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(), Off: vi.fn() },
  Create: {
    Nullable: (fn: any) => (v: any) => (v == null ? null : fn(v)),
    Array: (fn: any) => (arr: any[]) => (arr ?? []).map(fn),
    Struct: (ctor: any) => (v: any) => (v == null ? null : new ctor(v)),
    Map: (k: any, v: any) => (val: any) => val,
  },
  Call: { ByID: vi.fn().mockResolvedValue(null) },
}))

const updateFn = vi.fn().mockResolvedValue(undefined)
vi.mock('../../bindings/airmedy/internal/infra/wails/libraryservice', () => ({
  UpdateTrackMetadata: (...args: unknown[]) => updateFn(...args),
  GetFavoriteTracks: vi.fn().mockResolvedValue([]),
  ShowInExplorer: vi.fn(),
  ToggleFavorite: vi.fn(),
}))

function makeTrack(): TrackDTO {
  return new TrackDTO({
    id: 'track-1',
    title: 'My Song',
    year: 2024,
    track_number: 3,
    total_tracks: 10,
    disc_number: 1,
    total_discs: 1,
    raw_artist_names: 'Artist One',
    raw_genre_names: 'Rock',
    raw_composer_names: 'Composer A',
    album: { id: 'alb-1', title: 'My Album' } as any,
  })
}

function mountDialog(props: Record<string, unknown> = {}) {
  return mount(MetadataEditDialog, {
    props: { open: true, track: makeTrack(), ...props },
    global: { 
      plugins: [createTestI18n(), setupTestPinia()],
      stubs: { Teleport: true, Transition: true } 
    },
  })
}

describe('MetadataEditDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders form when open', () => {
    const w = mountDialog()
    expect(w.text()).toContain('library.edit_metadata')
  })

  it('initializes title input from track', () => {
    const w = mountDialog()
    const inputs = w.findAll('input')
    // Index 0 is the hidden file input, index 1 is Title
    const titleInput = inputs[1]
    expect((titleInput.element as HTMLInputElement).value).toBe('My Song')
  })

  it('calls UpdateTrackMetadata on save', async () => {
    const w = mountDialog()
    const saveBtn = w.findAll('button').find(b => b.text() === 'common.save')
    await saveBtn!.trigger('click')
    expect(updateFn).toHaveBeenCalledWith('track-1', expect.any(Object))
  })

  it('emits update:open=false after successful save', async () => {
    const w = mountDialog()
    const saveBtn = w.findAll('button').find(b => b.text() === 'common.save')
    await saveBtn!.trigger('click')
    await w.vm.$nextTick()
    const updateOpen = w.emitted('update:open')
    expect(updateOpen).toBeTruthy()
    expect(updateOpen![updateOpen!.length - 1]).toEqual([false])
  })

  it('closes on Cancel click', async () => {
    const w = mountDialog()
    const cancelBtn = w.findAll('button').find(b => b.text() === 'common.cancel')
    await cancelBtn!.trigger('click')
    expect(w.emitted('update:open')).toEqual([[false]])
  })

  it('does not render dialog when closed', () => {
    const w = mountDialog({ open: false, track: null })
    expect(w.text()).not.toContain('metadata.edit_title')
  })
})
