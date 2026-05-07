import { Music, Pencil, Trash2, ListEnd, ListPlus, Download } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { usePlaylistsStore } from '@/stores/playlists'
import type { ContextMenuItem } from './useContextMenu'
import type { Playlist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import * as PlaylistService from '../../bindings/airmedy/internal/infra/wails/playlistservice'

export interface PlaylistContextMenuOptions {
  includePlayNext?: boolean
  includePlaylistMenu?: boolean
  onRename?: (playlist: Playlist) => void
  onDelete?: (playlist: Playlist) => void
  includeExport?: boolean
}

export function usePlaylistContextMenu() {
  const { t } = useI18n()
  const playlistsStore = usePlaylistsStore()

  function buildMenuItems(playlist: Playlist, options: PlaylistContextMenuOptions = {}): ContextMenuItem[] {
    const items: ContextMenuItem[] = []

    // Play Next
    if (options.includePlayNext) {
      items.push({
        label: t('context_menu.play_next'),
        icon: ListEnd,
        action: async () => {
          const tracks = await PlaylistService.GetPlaylistTracks(playlist.id)
          PlayerService.PlayNextTracks(tracks.filter((t): t is TrackDTO => t !== null))
        },
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
            disabled: playlistsStore.playlists.length <= 1,
            children: playlistsStore.playlists
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
