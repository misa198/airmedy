import { Heart, ListEnd, ListPlus, Disc, User, Pencil, Trash2 } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { usePlaylistsStore } from '@/stores/playlists'
import { useFavoritesStore } from '@/stores/favorites'
import type { ContextMenuItem } from './useContextMenu'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

export function useTrackContextMenu(onEditMetadata: (track: TrackDTO) => void) {
  const playlistsStore = usePlaylistsStore()
  const favoritesStore = useFavoritesStore()
  const router = useRouter()

  function buildMenuItems(track: TrackDTO): ContextMenuItem[] {
    return [
      {
        label: 'Play Next',
        icon: ListEnd,
        action: () => { PlayerService.PlayNext(track) },
      },
      { separator: true },
      {
        label: track.is_favorite ? 'Remove from Favorites' : 'Add to Favorites',
        icon: Heart,
        action: async () => {
          await favoritesStore.toggle(track.id)
          track.is_favorite = !track.is_favorite
        },
      },
      {
        label: 'Add to Playlist',
        icon: ListPlus,
        children: playlistsStore.playlists.length
          ? playlistsStore.playlists.map(p => ({
              label: p.name,
              action: () => { PlaylistService.AddTrackToPlaylist(p.id, track.id) },
            }))
          : [{ label: 'No playlists', disabled: true }],
      },
      { separator: true },
      {
        label: 'Go to Album',
        icon: Disc,
        disabled: !track.album?.id,
        action: () => {
          if (track.album?.id) router.push(`/albums/${track.album.id}`)
        },
      },
      {
        label: 'Go to Artist',
        icon: User,
        disabled: !track.artists?.[0]?.id,
        action: () => {
          if (track.artists?.[0]?.id) router.push(`/artists/${track.artists[0].id}`)
        },
      },
      { separator: true },
      {
        label: 'Edit Metadata',
        icon: Pencil,
        action: () => onEditMetadata(track),
      },
      {
        label: 'Remove from Library',
        icon: Trash2,
        danger: true,
        action: () => { LibraryService.DeleteTrack(track.id) },
      },
    ]
  }

  return { buildMenuItems }
}
