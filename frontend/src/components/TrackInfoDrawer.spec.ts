import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import TrackInfoDrawer from './TrackInfoDrawer.vue'
import { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

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

function makeTrack(overrides: Record<string, unknown> = {}): TrackDTO {
  return new TrackDTO({
    id: 'track-1',
    title: 'My Song',
    format: 'flac',
    bitrate: 1000,
    sample_rate: 44100,
    bit_depth: 16,
    codec: '',
    ...overrides,
  } as any)
}

function mountDrawer(track: TrackDTO) {
  return mount(TrackInfoDrawer, {
    global: {
      plugins: [
        createTestingPinia({
          createSpy: vi.fn,
          initialState: {
            player: { trackInfoTrack: track },
          },
        }),
      ],
    },
  })
}

describe('TrackInfoDrawer quality badge', () => {
  it('shows LOSSY badge for mp3', () => {
    const w = mountDrawer(makeTrack({ format: 'mp3', bit_depth: 0, sample_rate: 44100, bitrate: 320 }))
    expect(w.text()).toContain('track_info.lossy')
  })

  it('shows LOSSLESS badge for 16-bit/44.1kHz flac', () => {
    const w = mountDrawer(makeTrack({ format: 'flac', bit_depth: 16, sample_rate: 44100 }))
    expect(w.text()).toContain('track_info.lossless')
  })

  it('shows HI-RES badge for 24-bit/96kHz flac', () => {
    const w = mountDrawer(makeTrack({ format: 'flac', bit_depth: 24, sample_rate: 96000 }))
    expect(w.text()).toContain('track_info.hi_res')
  })

  it('shows DSD badge for dsf', () => {
    const w = mountDrawer(makeTrack({ format: 'dsf', bit_depth: 1, sample_rate: 2822400 }))
    expect(w.text()).toContain('track_info.dsd')
  })

  it('distinguishes m4a AAC (lossy) from ALAC (lossless) via codec', () => {
    const aac = mountDrawer(makeTrack({ format: 'm4a', codec: 'aac', bit_depth: 16, sample_rate: 44100 }))
    expect(aac.text()).toContain('track_info.lossy')

    const alac = mountDrawer(makeTrack({ format: 'm4a', codec: 'alac', bit_depth: 16, sample_rate: 44100 }))
    expect(alac.text()).toContain('track_info.lossless')
  })

  it('hides the badge for legacy m4a rows with no codec yet (no blind guessing)', () => {
    const w = mountDrawer(makeTrack({ format: 'm4a', codec: '', bit_depth: 0, bitrate: 1000, sample_rate: 44100 }))
    expect(w.text()).not.toContain('track_info.lossless')
    expect(w.text()).not.toContain('track_info.lossy')
    expect(w.text()).not.toContain('track_info.hi_res')
    expect(w.text()).not.toContain('track_info.dsd')
  })

  it('shows bit depth in the details list when present', () => {
    const w = mountDrawer(makeTrack({ format: 'flac', bit_depth: 24, sample_rate: 96000 }))
    expect(w.text()).toContain('24-bit')
  })
})
