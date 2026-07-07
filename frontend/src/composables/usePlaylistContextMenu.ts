import { Music, Pencil, Trash2, ListStart, ListEnd, ListPlus, Download, Pin, PinOff, Play, Shuffle, Sparkles } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { usePlaylistsStore } from '@/stores/playlists'
import { usePlayerStore } from '@/stores/player'
import type { ContextMenuItem } from './useContextMenu'
import type { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

export interface PlaylistContextMenuOptions {
  includePlaylistMenu?: boolean
  onRename?: (playlist: Playlist) => void
  onDelete?: (playlist: Playlist) => void
  onEditSmartRules?: (playlist: Playlist) => void
  includeExport?: boolean
}

export function usePlaylistContextMenu() {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()
  const playerStore = usePlayerStore()

  async function getTracks(playlist: Playlist): Promise<TrackDTO[]> {
    const tracks = playlist.id === 'favorites'
      ? await LibraryService.GetFavoriteTracks()
      : await PlaylistService.GetPlaylistTracks(playlist.id)
    return tracks.filter((t): t is TrackDTO => t !== null)
  }

  function buildMenuItems(playlist: Playlist, options: PlaylistContextMenuOptions = {}): ContextMenuItem[] {
    const items: ContextMenuItem[] = []

    // Play
    items.push({
      label: t('context_menu.play'),
      icon: Play,
      action: async () => {
        const tracks = await getTracks(playlist)
        playerStore.playTracks(tracks, 0)
      },
    })

    // Shuffle
    items.push({
      label: t('context_menu.shuffle'),
      icon: Shuffle,
      action: async () => {
        const tracks = await getTracks(playlist)
        playerStore.shuffleTracks(tracks)
      },
    })

    // Play Next
    items.push({
      label: t('context_menu.play_next'),
      icon: ListStart,
      action: async () => {
        const tracks = await getTracks(playlist)
        PlayerService.PlayNextTracks(tracks)
      },
    })

    // Add to Queue
    items.push({
      label: t('context_menu.add_to_queue'),
      icon: ListEnd,
      action: async () => {
        const tracks = await getTracks(playlist)
        const toAdd = tracks.filter(t => !playerStore.queueIds.has(t.id))
        if (toAdd.length > 0) {
          PlayerService.AppendTracks(toAdd)
        }
      },
    })

    // Pin/Unpin to sidebar
    {
      const pinned = playlistsStore.isPinned(playlist)
      items.push({
        label: pinned ? t('context_menu.unpin_from_sidebar') : t('context_menu.pin_to_sidebar'),
        icon: pinned ? PinOff : Pin,
        action: () => playlistsStore.togglePinned(playlist.id),
      })
    }

    // Rename
    if (options.onRename && playlist.id !== 'favorites') {
      items.push({
        label: t('sidebar.rename'),
        icon: Pencil,
        action: () => options.onRename!(playlist),
      })
    }

    // Edit rules (smart playlists only)
    if (options.onEditSmartRules && playlist.is_smart) {
      items.push({
        label: t('context_menu.edit_rules'),
        icon: Sparkles,
        action: () => options.onEditSmartRules!(playlist),
      })
    }

    // Export to M3U8
    if (options.includeExport !== false && playlist.id !== 'favorites') {
      items.push({
        label: t('context_menu.export_playlist'),
        icon: Download,
        action: () => PlaylistService.ExportPlaylistToM3U8(playlist.id),
      })
    }

    // Delete (Top level if requested)
    if (options.onDelete && !options.includePlaylistMenu && playlist.id !== 'favorites') {
      items.push({
        label: t('sidebar.delete'),
        icon: Trash2,
        danger: true,
        action: () => options.onDelete!(playlist),
      })
    }

    // Playlist sub-menu
    if (options.includePlaylistMenu) {
      items.push({
        label: t('library.playlist'),
        icon: Music,
        children: [
          {
            label: t('context_menu.add_to_playlist'),
            icon: ListPlus,
            disabled: playlistsStore.manualPlaylists.filter(p => p.id !== playlist.id).length === 0,
            children: playlistsStore.manualPlaylists
              .filter(p => p.id !== playlist.id)
              .map(p => ({
                label: p.name,
                action: async () => {
                  const tracks = await PlaylistService.GetPlaylistTracks(playlist.id)
                  for (const track of tracks) {
                    if (track) await PlaylistService.AddTrackToPlaylist(p.id, track.id, '')
                  }
                },
              })),
          },
          ...(options.onDelete && playlist.id !== 'favorites' ? [
            {
              label: t('sidebar.delete'),
              icon: Trash2,
              danger: true,
              action: () => options.onDelete!(playlist),
            }
          ] : [])
        ],
      })
    }

    return items
  }

  return { buildMenuItems }
}
