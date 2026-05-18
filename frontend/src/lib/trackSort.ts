import type { TrackDTO, AlbumDTO } from '../../bindings/airmedy/internal/domain/models'

export function sortTracksGrouped(tracks: TrackDTO[], albums?: AlbumDTO[]): TrackDTO[] {
  const groups: Record<string, { album: AlbumDTO | null, tracks: TrackDTO[] }> = {}
  const unknownAlbumId = 'unknown'

  if (albums) {
    for (const album of albums) {
      groups[album.id] = { album, tracks: [] }
    }
  }

  for (const track of tracks) {
    const albumId = track.album?.id || unknownAlbumId
    if (!groups[albumId]) {
      groups[albumId] = { album: track.album || null, tracks: [] }
    }
    groups[albumId].tracks.push(track)
  }

  const result = Object.values(groups).filter(g => g.tracks.length > 0)

  result.sort((a, b) => {
    if (a.album?.id === unknownAlbumId) return 1
    if (b.album?.id === unknownAlbumId) return -1
    const yearA = a.album?.year || 0
    const yearB = b.album?.year || 0
    if (yearA !== yearB) return yearB - yearA
    return (a.album?.title || '').localeCompare(b.album?.title || '')
  })

  for (const group of result) {
    group.tracks.sort((t1, t2) => {
      const d1 = t1.disc_number || 1
      const d2 = t2.disc_number || 1
      if (d1 !== d2) return d1 - d2
      return (t1.track_number || 0) - (t2.track_number || 0)
    })
  }

  return result.flatMap(g => g.tracks)
}
