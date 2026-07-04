import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'

export type AudioQualityTier = 'LOSSY' | 'LOSSLESS' | 'HI-RES' | 'DSD' | 'UNKNOWN'

const LOSSY_EXTENSIONS = new Set(['mp3', 'aac', 'ogg', 'opus'])
const CONTAINER_EXTENSIONS = new Set(['m4a', 'mp4'])
const DSD_EXTENSIONS = new Set(['dsf', 'dff'])
const LOSSLESS_EXTENSIONS = new Set(['flac', 'wav', 'aiff', 'ape', 'wv', 'm4a', 'mp4'])

// bit_depth === 0 / codec === '' means the track hasn't been rescanned since
// bit-depth/codec extraction was added (legacy row). m4a/mp4 can't be told
// apart (AAC vs ALAC) from bitrate/sample_rate alone, so rather than guess
// blind, report UNKNOWN (badge hidden) until the library is re-synced.
export function determineAudioQuality(track: TrackDTO): AudioQualityTier {
  const ext = (track.format || '').toLowerCase()

  if (LOSSY_EXTENSIONS.has(ext)) return 'LOSSY'

  if (CONTAINER_EXTENSIONS.has(ext)) {
    if (!track.codec) return 'UNKNOWN'
    if (track.codec !== 'alac') return 'LOSSY'
    // ALAC confirmed, fall through to Hi-Res/Lossless tiering below.
  } else if (DSD_EXTENSIONS.has(ext)) {
    return 'DSD'
  }

  if (LOSSLESS_EXTENSIONS.has(ext)) {
    const bitDepth = track.bit_depth || 0
    const isHiRes = (bitDepth > 0 && bitDepth > 16) || track.sample_rate > 48000
    if (isHiRes) return 'HI-RES'
    return 'LOSSLESS'
  }

  return 'UNKNOWN'
}
