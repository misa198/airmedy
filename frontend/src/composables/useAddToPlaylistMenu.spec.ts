import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createTestingPinia } from '@pinia/testing'
import { setActivePinia } from 'pinia'
import { usePlaylistsStore } from '@/stores/playlists'
import { useAddToPlaylistMenu } from './useAddToPlaylistMenu'
import { useCreatePlaylistWithTracks } from './useCreatePlaylistWithTracks'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}))

vi.mock('../../bindings/airmedy/internal/infra/wails/playlistservice', () => ({
  AddTracksToPlaylist: vi.fn(),
}))

vi.mock('@wailsio/runtime', () => ({
  Events: { On: vi.fn(() => vi.fn()) },
}))

describe('useAddToPlaylistMenu', () => {
  beforeEach(() => {
    setActivePinia(createTestingPinia({ createSpy: vi.fn }))
    useCreatePlaylistWithTracks().close()
  })

  it('puts Create Playlist before a divider', async () => {
    const { buildCreatePlaylistItems } = useAddToPlaylistMenu()
    const items = buildCreatePlaylistItems(['track-1'])

    expect(items[0].label).toBe('sidebar.new_playlist')
    expect(items[0].icon).toBeUndefined()
    expect(items[1].separator).toBe(true)

    await items[0].action?.()
    expect(useCreatePlaylistWithTracks().isVisible.value).toBe(true)
  })

  it('creates a playlist and adds every pending track to it', async () => {
    const playlistsStore = usePlaylistsStore()
    vi.mocked(playlistsStore.create).mockResolvedValue({ id: 'playlist-1' } as never)
    const dialog = useCreatePlaylistWithTracks()
    dialog.open(['track-1', 'track-2', 'track-1'])

    await dialog.create('Road trip')

    expect(PlaylistService.AddTracksToPlaylist).toHaveBeenCalledWith(
      'playlist-1',
      ['track-1', 'track-2'],
      '',
    )
  })
})
