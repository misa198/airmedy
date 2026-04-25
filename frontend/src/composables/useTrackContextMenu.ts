import { Heart, ListEnd, ListPlus, Disc, User, Pencil, Trash2, Info, RefreshCw } from 'lucide-vue-next'
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
import * as LyricsService from '../../bindings/airmedy/internal/infra/wails/lyricsservice'

export interface TrackContextMenuOptions {
  excludePlayNext?: boolean
  excludeDelete?: boolean
}

export function useTrackContextMenu(onEditMetadata: (track: TrackDTO) => void) {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()
  const favoritesStore = useFavoritesStore()
  const playerStore = usePlayerStore()
  const router = useRouter()

  function buildMenuItems(track: TrackDTO, options: TrackContextMenuOptions = {}): ContextMenuItem[] {
    const items: ContextMenuItem[] = []

    if (!options.excludePlayNext) {
      items.push({
        label: t('context_menu.play_next'),
        icon: ListEnd,
        action: () => { PlayerService.PlayNext(track) },
      })
    }

    items.push({
      label: t('context_menu.track_info'),
      icon: Info,
      action: () => { playerStore.openTrackInfo(track) },
    })

    items.push({
      label: t('context_menu.refresh_lyrics'),
      icon: RefreshCw,
      action: async () => {
        const isCurrentTrack = playerStore.currentTrack?.id === track.id
        if (isCurrentTrack) playerStore.lyricsLoading = true
        const lyric = await LyricsService.FetchLyrics(track.id, track)
        if (isCurrentTrack) {
          playerStore.lyrics = lyric ?? null
          playerStore.lyricsLoading = false
        }
      },
    })

    items.push({ separator: true })

    const isFavorite = favoritesStore.isFavorite(track)
    items.push({
      label: isFavorite ? t('context_menu.remove_from_favorites') : t('context_menu.add_to_favorites'),
      icon: Heart,
      action: async () => {
        await favoritesStore.toggle(track.id)
      },
    })

    items.push({
      label: t('context_menu.add_to_playlist'),
      icon: ListPlus,
      children: playlistsStore.playlists.length
        ? playlistsStore.playlists.map(p => ({
            label: p.name,
            action: () => { PlaylistService.AddTrackToPlaylist(p.id, track.id) },
          }))
        : [{ label: t('context_menu.no_playlists'), disabled: true }],
    })

    items.push({ separator: true })

    items.push({
      label: t('context_menu.go_to_album'),
      icon: Disc,
      disabled: !track.album?.id,
      action: () => {
        if (track.album?.id) router.push(`/albums/${track.album.id}`)
      },
    })

    const artistItems = (track.artists || [])
      .filter((a): a is NonNullable<typeof a> => !!a && !!a.id)
      .map(a => ({
        label: a.name,
        icon: User,
        action: () => router.push(`/artists/${a.id}`),
      }))

    items.push({
      label: t('context_menu.go_to_artist'),
      icon: User,
      disabled: artistItems.length === 0,
      action: artistItems.length === 1 ? artistItems[0].action : undefined,
      children: artistItems.length > 1 ? artistItems : undefined,
    })

    items.push({ separator: true })

    items.push({
      label: t('context_menu.edit_metadata'),
      icon: Pencil,
      action: () => onEditMetadata(track),
    })

    if (!options.excludeDelete) {
      items.push({
        label: t('context_menu.remove_from_library'),
        icon: Trash2,
        danger: true,
        action: () => { LibraryService.DeleteTrack(track.id) },
      })
    }

    return items
  }

  return { buildMenuItems }
}
