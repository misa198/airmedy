import { Heart, ListEnd, ListPlus, Disc, User, Pencil, Trash2, Info } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePlaylistsStore } from '@/stores/playlists'
import { useFavoritesStore } from '@/stores/favorites'
import { usePlayerStore } from '@/stores/player'
import type { ContextMenuItem } from './useContextMenu'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

export function useTrackContextMenu(onEditMetadata: (track: TrackDTO) => void) {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()
  const favoritesStore = useFavoritesStore()
  const playerStore = usePlayerStore()
  const router = useRouter()

  function buildMenuItems(track: TrackDTO): ContextMenuItem[] {
    return [
      {
        label: t('context_menu.play_next'),
        icon: ListEnd,
        action: () => { PlayerService.PlayNext(track) },
      },
      {
        label: t('context_menu.track_info'),
        icon: Info,
        action: () => { playerStore.openTrackInfo(track) },
      },
      { separator: true },
      {
        label: track.is_favorite ? t('context_menu.remove_from_favorites') : t('context_menu.add_to_favorites'),
        icon: Heart,
        action: async () => {
          await favoritesStore.toggle(track.id)
          track.is_favorite = !track.is_favorite
        },
      },
      {
        label: t('context_menu.add_to_playlist'),
        icon: ListPlus,
        children: playlistsStore.playlists.length
          ? playlistsStore.playlists.map(p => ({
              label: p.name,
              action: () => { PlaylistService.AddTrackToPlaylist(p.id, track.id) },
            }))
          : [{ label: t('context_menu.no_playlists'), disabled: true }],
      },
      { separator: true },
      {
        label: t('context_menu.go_to_album'),
        icon: Disc,
        disabled: !track.album?.id,
        action: () => {
          if (track.album?.id) router.push(`/albums/${track.album.id}`)
        },
      },
      {
        label: t('context_menu.go_to_artist'),
        icon: User,
        disabled: !track.artists?.[0]?.id,
        action: () => {
          if (track.artists?.[0]?.id) router.push(`/artists/${track.artists[0].id}`)
        },
      },
      { separator: true },
      {
        label: t('context_menu.edit_metadata'),
        icon: Pencil,
        action: () => onEditMetadata(track),
      },
      {
        label: t('context_menu.remove_from_library'),
        icon: Trash2,
        danger: true,
        action: () => { LibraryService.DeleteTrack(track.id) },
      },
    ]
  }

  return { buildMenuItems }
}
