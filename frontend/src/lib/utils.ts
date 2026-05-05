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
  const hours = Math.floor(totalSeconds / 3600)
  const mins = Math.floor((totalSeconds % 3600) / 60)
  if (hours > 0) return `${hours} ${t('common.hr')} ${mins} ${t('common.min')}`
  return `${mins} ${t('common.min')}`
}

export function foldUnicode(s: string): string {
  if (!s) return ''
  return s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
}