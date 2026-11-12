import { Clock, Disc, Home, ListMusic, Music, PenTool, Users } from '@lucide/vue'

export type SidebarItemId = 'home' | 'recently_added' | 'artists' | 'albums' | 'tracks' | 'genres' | 'composers'
export interface SidebarItem { id: SidebarItemId; visible: boolean }

export const SIDEBAR_ITEMS = {
  home: { path: '/', label: 'sidebar.home', icon: Home },
  recently_added: { path: '/recently-added', label: 'sidebar.recently_added', icon: Clock },
  artists: { path: '/artists', label: 'sidebar.artists', icon: Users },
  albums: { path: '/albums', label: 'sidebar.albums', icon: Disc },
  tracks: { path: '/tracks', label: 'sidebar.tracks', icon: Music },
  genres: { path: '/genres', label: 'sidebar.genres', icon: ListMusic },
  composers: { path: '/composers', label: 'sidebar.composers', icon: PenTool },
} as const

export function defaultSidebarItems(): SidebarItem[] {
  return Object.keys(SIDEBAR_ITEMS).map(id => ({ id: id as SidebarItemId, visible: true }))
}
