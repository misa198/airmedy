import { ListEnd, ListPlus } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { usePlaylistsStore } from '@/stores/playlists'
import type { ContextMenuItem } from './useContextMenu'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'

export function useGroupContextMenu() {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()

  function buildMenuItems(tracks: TrackDTO[]): ContextMenuItem[] {
    return [
      {
        label: t('context_menu.play_next'),
        icon: ListEnd,
        action: () => { PlayerService.PlayNextTracks(tracks) },
      },
      {
        label: t('context_menu.add_to_playlist'),
        icon: ListPlus,
        children: playlistsStore.playlists.length
          ? playlistsStore.playlists.map(p => ({
              label: p.name,
              action: async () => {
                // Add all tracks to playlist
                for (const track of tracks) {
                  await PlaylistService.AddTrackToPlaylist(p.id, track.id, '')
                }
              },
            }))
          : [{ label: t('context_menu.no_playlists'), disabled: true }],
      },
    ]
  }

  return { buildMenuItems }
}
