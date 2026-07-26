import { useI18n } from 'vue-i18n'
import type { ContextMenuItem } from './useContextMenu'
import { useCreatePlaylistWithTracks } from './useCreatePlaylistWithTracks'

export function useAddToPlaylistMenu() {
  const { t } = useI18n()
  const createPlaylistWithTracks = useCreatePlaylistWithTracks()

  function buildCreatePlaylistItems(trackIDs: string[] | (() => Promise<string[]>)): ContextMenuItem[] {
    return [
      {
        label: t('sidebar.new_playlist'),
        action: async () => {
          createPlaylistWithTracks.open(typeof trackIDs === 'function' ? await trackIDs() : trackIDs)
        },
      },
      { separator: true },
    ]
  }

  return { buildCreatePlaylistItems }
}
