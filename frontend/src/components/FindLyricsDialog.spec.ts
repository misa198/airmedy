import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createTestI18n } from '@airmedy/utils'

const saveLyrics = vi.fn().mockResolvedValue(undefined)
const searchLyrics = vi.fn()
const publishCurrentLyrics = vi.fn().mockResolvedValue(3)

vi.mock('../../bindings/airmedy/internal/infra/wails/lyricsservice', () => ({
  SaveLyrics: (...args: unknown[]) => saveLyrics(...args),
  SaveLyricsFile: vi.fn(),
  SearchLyrics: (...args: unknown[]) => searchLyrics(...args),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/playerservice', () => ({
  PublishCurrentLyrics: (...args: unknown[]) => publishCurrentLyrics(...args),
}))

vi.mock('@/stores/player', () => ({
  usePlayerStore: () => ({
    currentTrack: { id: 'track-1' },
    lyrics: null,
  }),
}))

import FindLyricsDialog from './FindLyricsDialog.vue'
import { useFindLyricsDialog } from '@/composables/useFindLyricsDialog'

function mountDialog() {
  return mount(FindLyricsDialog, {
    global: {
      plugins: [createTestI18n()],
      stubs: {
        Modal: { template: '<div><slot /></div>' },
        Input: { template: '<input />' },
        Checkbox: { template: '<span />' },
        Tooltip: { template: '<span><slot /></span>' },
      },
    },
  })
}

describe('FindLyricsDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useFindLyricsDialog().close()
  })

  it('publishes a selected lyric for the playing track after saving it', async () => {
    searchLyrics.mockResolvedValue([{
      track_name: 'Song',
      artist_name: 'Artist',
      duration: 180,
      provider: 'lrclib',
      content: '[00:01.00]Chosen lyric',
      source: 'lrclib-synced',
    }])
    const wrapper = mountDialog()
    useFindLyricsDialog().open({ id: 'track-1', title: 'Song', duration: 180, artists: [{ name: 'Artist' }] } as any)
    await nextTick()

    await wrapper.findAll('button').find(button => button.text() === 'find_lyrics.search')!.trigger('click')
    await nextTick()
    await nextTick()
    await wrapper.findAll('div').find(div => div.text().includes('Song') && div.classes().includes('cursor-pointer'))!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === 'find_lyrics.select')!.trigger('click')

    expect(saveLyrics).toHaveBeenCalledWith('track-1', '[00:01.00]Chosen lyric', 'lrclib-synced')
    expect(publishCurrentLyrics).toHaveBeenCalledWith(expect.objectContaining({
      track_id: 'track-1',
      content: '[00:01.00]Chosen lyric',
      source: 'lrclib-synced',
    }))
  })
})
