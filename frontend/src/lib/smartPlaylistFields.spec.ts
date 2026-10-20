import { describe, expect, it } from 'vitest'
import { playlistDependsOnFavorites } from './smartPlaylistFields'

describe('playlistDependsOnFavorites', () => {
  it('recognizes Favorites and nested live rules', () => {
    expect(playlistDependsOnFavorites({ id: 'favorites' })).toBe(true)
    for (const value of [false, true]) {
      expect(playlistDependsOnFavorites({ id: 'smart', is_smart: true, rules: JSON.stringify({
        live_updating: true, root: { groups: [{ groups: [{ rules: [{ field: 'is_favorite', value }] }] }] },
      }) })).toBe(true)
    }
  })
  it('excludes regular playlists, snapshots and unrelated smart rules', () => {
    expect(playlistDependsOnFavorites({ id: 'normal' })).toBe(false)
    for (const live of [false, true]) {
      expect(playlistDependsOnFavorites({ id: 'smart', is_smart: true, rules: JSON.stringify({
        live_updating: live, root: { rules: [{ field: live ? 'genre' : 'is_favorite' }] },
      }) })).toBe(false)
    }
  })
  it('refreshes conservatively for malformed stored rules', () => {
    expect(playlistDependsOnFavorites({ id: 'smart', is_smart: true, rules: '{' })).toBe(true)
  })
})
