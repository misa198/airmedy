import { ListEnd, ListPlus, ListStart } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { usePlaylistsStore } from '@/stores/playlists'
import { usePlayerStore } from '@/stores/player'
import { useAddToPlaylistMenu } from './useAddToPlaylistMenu'
import type { ContextMenuItem } from './useContextMenu'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'

export function useGroupContextMenu() {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()
  const playerStore = usePlayerStore()
  const { buildCreatePlaylistItems } = useAddToPlaylistMenu()

  function buildMenuItems(tracks: TrackDTO[]): ContextMenuItem[] {
    const toAdd = tracks.filter(track => !playerStore.queueIds.has(track.id))

    return [
      {
        label: t('context_menu.play_next'),
        icon: ListStart,
        action: () => { PlayerService.PlayNextTracks(tracks) },
      },
      {
        label: t('context_menu.add_to_queue'),
        icon: ListEnd,
        action: () => {
          if (toAdd.length > 0) PlayerService.AppendTracks(toAdd)
        },
      },
      {
        label: t('context_menu.add_to_playlist'),
        icon: ListPlus,
        children: [
          ...buildCreatePlaylistItems(tracks.map(track => track.id)),
          ...(playlistsStore.manualPlaylists.length
            ? playlistsStore.manualPlaylists.map(p => ({
              label: p.name,
              action: async () => {
                // Add all tracks to playlist
                for (const track of tracks) {
                  await PlaylistService.AddTrackToPlaylist(p.id, track.id, '')
                }
              },
            }))
            : [{ label: t('context_menu.no_playlists'), disabled: true }]),
        ],
      },
    ]
  }

  return { buildMenuItems }
}
