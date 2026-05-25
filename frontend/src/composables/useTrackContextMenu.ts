import { Heart, HeartOff, ListEnd, ListPlus, Disc, User, Pencil, FolderOpen, Info, RefreshCw, ListX, Check, Trash2, Search } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePlaylistsStore } from '@/stores/playlists'
import { useFavoritesStore } from '@/stores/favorites'
import { usePlayerStore } from '@/stores/player'
import { useFindLyricsDialog } from './useFindLyricsDialog'
import type { ContextMenuItem } from './useContextMenu'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import * as LyricsService from '../../bindings/airmedy/internal/infra/wails/lyricsservice'

export interface TrackContextMenuOptions {
  excludePlayNext?: boolean
  excludeDelete?: boolean
  showRemoveFromQueue?: boolean
  playlistId?: string
}

export function useTrackContextMenu(onEditMetadata: (track: TrackDTO) => void) {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()
  const favoritesStore = useFavoritesStore()
  const playerStore = usePlayerStore()
  const router = useRouter()
  const findLyricsDialog = useFindLyricsDialog()

  function buildMenuItems(track: TrackDTO, options: TrackContextMenuOptions = {}): ContextMenuItem[] {
    const items: ContextMenuItem[] = []

    const closeFullScreen = () => {
      if (playerStore.playerMode === 'fullscreen') {
        playerStore.playerMode = 'sticky'
      }
    }

    const isCurrentTrack = playerStore.currentTrack?.id === track.id

    if (options.showRemoveFromQueue && !isCurrentTrack) {
      items.push({
        label: t('context_menu.remove_from_queue'),
        icon: ListX,
        action: () => { PlayerService.RemoveFromQueue(track.id) },
      })
      items.push({ separator: true })
    }

    if (!options.excludePlayNext && !isCurrentTrack) {
      items.push({
        label: t('context_menu.play_next'),
        icon: ListEnd,
        action: () => { PlayerService.PlayNext(track) },
      })
    }

    items.push({
      label: t('context_menu.track_info'),
      icon: Info,
      action: () => {
        closeFullScreen()
        playerStore.openTrackInfo(track)
      },
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

    items.push({
      label: t('context_menu.find_lyrics'),
      icon: Search,
      action: () => {
        if (playerStore.playerMode === 'fullscreen') {
          closeFullScreen()
          setTimeout(() => {
            findLyricsDialog.open(track)    
          }, 300)
        } else {
          findLyricsDialog.open(track)
        }        
      },
    })

    items.push({ separator: true })

    const isFavorite = favoritesStore.isFavorite(track)
    items.push({
      label: isFavorite ? t('context_menu.remove_from_favorites') : t('context_menu.add_to_favorites'),
      icon: isFavorite ? HeartOff : Heart,
      action: async () => {
        await favoritesStore.toggle(track.id)
      },
    })

    if (!options.playlistId) {
      const playlistChildren: ContextMenuItem[] = playlistsStore.playlists.length
        ? playlistsStore.playlists.map(p => ({
          label: p.name,
          action: () => { PlaylistService.AddTrackToPlaylist(p.id, track.id, '') },
        }))
        : [{ label: t('context_menu.no_playlists'), disabled: true }]

      items.push({
        label: t('context_menu.add_to_playlist'),
        icon: ListPlus,
        children: playlistChildren,
      })

      // Async check for playlists that already contain this track
      PlaylistService.GetPlaylistsForTrack(track.id).then(playlistIds => {
        if (!playlistIds || !playlistIds.length) return

        playlistChildren.forEach((child, index) => {
          const p = playlistsStore.playlists[index]
          if (p && playlistIds.includes(p.id)) {
            child.iconRight = Check
            child.action = () => { PlaylistService.RemoveTrackFromPlaylist(p.id, track.id, '') }
          }
        })
      })
    }

    items.push({ separator: true })

    items.push({
      label: t('context_menu.go_to_album'),
      icon: Disc,
      disabled: !track.album?.id,
      action: () => {
        if (track.album?.id) {
          closeFullScreen()
          router.push(`/albums/${track.album.id}`)
        }
      },
    })

    const artistItems = (track.artists || [])
      .filter((a): a is NonNullable<typeof a> => !!a && !!a.id)
      .map(a => ({
        label: a.name,
        icon: User,
        action: () => {
          closeFullScreen()
          router.push(`/artists/${a.id}`)
        },
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
      action: () => {
        closeFullScreen()
        onEditMetadata(track)
      },
    })

    items.push({
      label: t('context_menu.show_in_explorer'),
      icon: FolderOpen,
      action: () => { LibraryService.ShowInExplorer(track.id) },
    })

    if (options.playlistId && options.playlistId !== 'favorites') {
      items.push({ separator: true })
      items.push({
        label: t('context_menu.remove_from_playlist'),
        icon: Trash2,
        danger: true,
        action: () => {
          PlaylistService.RemoveTrackFromPlaylist(options.playlistId!, track.id, '')
        },
      })
    }

    return items
  }

  function buildMultiSelectMenuItems(tracks: TrackDTO[], options: TrackContextMenuOptions = {}): ContextMenuItem[] {
    const items: ContextMenuItem[] = []

    if (!options.excludePlayNext) {
      items.push({
        label: t('context_menu.play_next'),
        icon: ListEnd,
        action: () => { PlayerService.PlayNextTracks(tracks) },
      })
    }

    items.push({
      label: t('context_menu.refresh_lyrics'),
      icon: RefreshCw,
      action: async () => {
        for (const track of tracks) {
          await LyricsService.FetchLyrics(track.id, track)
        }
        // If current track is in the selection, update its lyrics in store
        if (playerStore.currentTrack && tracks.some(t => t.id === playerStore.currentTrack?.id)) {
          const lyric = await LyricsService.GetLyrics(playerStore.currentTrack.id)
          playerStore.lyrics = lyric ?? null
        }
      },
    })

    items.push({ separator: true })

    items.push({
      label: t('context_menu.add_to_favorites'),
      icon: Heart,
      action: async () => {
        for (const track of tracks) {
          if (!favoritesStore.isFavorite(track)) {
            await favoritesStore.toggle(track.id)
          }
        }
      },
    })

    if (!options.playlistId) {
      const playlistChildren: ContextMenuItem[] = playlistsStore.playlists.length
        ? playlistsStore.playlists.map(p => ({
          label: p.name,
          action: () => {
            PlaylistService.AddTracksToPlaylist(p.id, tracks.map(t => t.id), '')
          },
        }))
        : [{ label: t('context_menu.no_playlists'), disabled: true }]

      items.push({
        label: t('context_menu.add_to_playlist'),
        icon: ListPlus,
        children: playlistChildren,
      })
    }

    if (options.playlistId && options.playlistId !== 'favorites') {
      items.push({ separator: true })
      items.push({
        label: t('context_menu.remove_from_playlist'),
        icon: Trash2,
        danger: true,
        action: () => {
          tracks.forEach(track => {
            PlaylistService.RemoveTrackFromPlaylist(options.playlistId!, track.id, '')
          })
        },
      })
    }

    return items
  }

  return { buildMenuItems, buildMultiSelectMenuItems }
}
