import { ListStart, ListEnd, ListPlus, Play, Shuffle, Heart, HeartOff } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { usePlaylistsStore } from '@/stores/playlists'
import { usePlayerStore } from '@/stores/player'
import { useFavoritesStore } from '@/stores/favorites'
import { useAddToPlaylistMenu } from './useAddToPlaylistMenu'
import type { ContextMenuItem } from './useContextMenu'
import type { TrackDTO, AlbumDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

export interface AlbumContextMenuOptions {
  hidePlayShuffle?: boolean
}

export function useAlbumContextMenu() {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()
  const playerStore = usePlayerStore()
  const favoritesStore = useFavoritesStore()
  const { buildCreatePlaylistItems } = useAddToPlaylistMenu()

  async function getTracks(albumId: string, providedTracks?: TrackDTO[]): Promise<TrackDTO[]> {
    if (providedTracks && providedTracks.length > 0) return providedTracks
    const tracks = await LibraryService.GetTracksByAlbumID(albumId)
    return tracks.filter((t): t is TrackDTO => t !== null)
  }

  function buildMenuItems(album: AlbumDTO, providedTracks?: TrackDTO[], options: AlbumContextMenuOptions = {}): ContextMenuItem[] {
    const fetchTracks = () => getTracks(album.id, providedTracks)

    // Check if any track is favorite to decide label
    const allTracksLoaded = providedTracks && providedTracks.length > 0
    const isAnyFavorite = allTracksLoaded ? providedTracks!.some(t => favoritesStore.isFavorite(t)) : false
    const allQueued = allTracksLoaded ? providedTracks!.every(t => playerStore.queueIds.has(t.id)) : false

    const items: ContextMenuItem[] = []

    if (!options.hidePlayShuffle) {
      items.push(
        {
          label: t('context_menu.play'),
          icon: Play,
          action: async () => {
            const tracks = await fetchTracks()
            if (tracks.length > 0) {
              playerStore.playTracks(tracks, 0)
            }
          },
        },
        {
          label: t('context_menu.shuffle'),
          icon: Shuffle,
          action: async () => {
            const tracks = await fetchTracks()
            if (tracks.length > 0) {
              playerStore.shuffleTracks(tracks)
            }
          },
        }
      )
    }

    items.push({
      label: t('context_menu.play_next'),
      icon: ListStart,
      action: async () => {
        const tracks = await fetchTracks()
        if (tracks.length > 0) {
          PlayerService.PlayNextTracks(tracks)
        }
      },
    })

    items.push({
      label: t('context_menu.add_to_queue'),
      icon: ListEnd,
      disabled: allQueued,
      tooltip: allQueued ? t('context_menu.already_in_queue') : undefined,
      action: async () => {
        const tracks = await fetchTracks()
        const toAdd = tracks.filter(t => !playerStore.queueIds.has(t.id))
        if (toAdd.length > 0) {
          PlayerService.AppendTracks(toAdd)
        }
      },
    })

    items.push(
      { separator: true },
      {
        label: isAnyFavorite ? t('context_menu.remove_from_favorites') : t('context_menu.add_to_favorites'),
        icon: isAnyFavorite ? HeartOff : Heart,
        action: async () => {
          const tracks = await fetchTracks()
          const targetState = !isAnyFavorite
          for (const track of tracks) {
            if (favoritesStore.isFavorite(track) !== targetState) {
              await favoritesStore.toggle(track.id)
            }
          }
        },
      },
      {
        label: t('context_menu.add_to_playlist'),
        icon: ListPlus,
        children: [
          ...buildCreatePlaylistItems(async () => (await fetchTracks()).map(track => track.id)),
          ...(playlistsStore.manualPlaylists.length
            ? playlistsStore.manualPlaylists.map(p => ({
              label: p.name,
              action: async () => {
                const tracks = await fetchTracks()
                for (const track of tracks) {
                  await PlaylistService.AddTrackToPlaylist(p.id, track.id, '')
                }
              },
            }))
            : [{ label: t('context_menu.no_playlists'), disabled: true }]),
        ],
      }
    )

    return items
  }

  return { buildMenuItems }
}
