import { type ClassValue, clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function formatTime(seconds: number): string {
  if (!isFinite(seconds) || seconds < 0) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

export function hexToRgba(hex: string, alpha: number): string {
  const clean = hex.replace('#', '')
  const r = parseInt(clean.substring(0, 2), 16)
  const g = parseInt(clean.substring(2, 4), 16)
  const b = parseInt(clean.substring(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

export function buildArtworkUrl(
  key: string | null | undefined,
  size: 'sm' | 'md' | 'lg' = 'lg',
): string | undefined {
  if (!key) return undefined
  if (size === 'lg') return `/artwork/${key}`
  return `/artwork/${key}?size=${size}`
}

export function getTrackDisplayTitle(track: { title?: string; path?: string }): string {
  if (track.title) return track.title
  if (track.path) {
    const parts = track.path.replace(/\\/g, '/').split('/')
    const filename = parts[parts.length - 1]
    return filename.replace(/\.[^.]+$/, '') || filename
  }
  return ''
}

export function formatTotalDuration(totalSeconds: number, t: (key: string) => string): string {
	if (!isFinite(totalSeconds) || totalSeconds < 0) return `0 ${t('common.sec')}`
	const days = Math.floor(totalSeconds / 86400)
	const hours = Math.floor((totalSeconds % 86400) / 3600)
	const mins = Math.floor((totalSeconds % 3600) / 60)
	const secs = Math.floor(totalSeconds % 60)
	if (days > 0) return `${days} ${t('common.day')} ${hours} ${t('common.hr')}`
	if (hours > 0) return `${hours} ${t('common.hr')} ${mins} ${t('common.min')}`
	if (mins === 0) return `${secs} ${t('common.sec')}`
	return `${mins} ${t('common.min')}`
}

export function foldUnicode(s: string): string {
  if (!s) return ''
  return s
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
}

export function decodeHTMLEntities(str: string): string {
  if (!str) return ''
  
  const htmlEntities: Record<string, string> = {
    amp: '&',
    apos: "'",
    lt: '<',
    gt: '>',
    quot: '"',
    nbsp: ' ',
  }

  return str.replace(/&(#(?:\d+|x[a-fA-F0-9]+)|[a-zA-Z]+);/g, (match, entity) => {
    if (entity.startsWith('#')) {
      if (entity[1]?.toLowerCase() === 'x') {
        const code = parseInt(entity.substring(2), 16)
        return isNaN(code) ? match : String.fromCharCode(code)
      } else {
        const code = parseInt(entity.substring(1), 10)
        return isNaN(code) ? match : String.fromCharCode(code)
      }
    }
    return htmlEntities[entity.toLowerCase()] || match
  })
}
