export const DEFAULT_PRIMARY_COLOR = '#E11D48'

const hexColorPattern = /^#[0-9a-fA-F]{6}$/

export function normalizeHexColor(value: string | undefined | null): string | null {
  const color = value?.trim() ?? ''
  return hexColorPattern.test(color) ? color.toUpperCase() : null
}

export function hexToRgbChannels(color: string): string {
  const normalized = normalizeHexColor(color) ?? DEFAULT_PRIMARY_COLOR
  return `${parseInt(normalized.slice(1, 3), 16)} ${parseInt(normalized.slice(3, 5), 16)} ${parseInt(normalized.slice(5, 7), 16)}`
}

function perceivedBrightness(color: string): number {
  const channels = hexToRgbChannels(color).split(' ').map(Number)
  const [red, green, blue] = channels
  return Math.sqrt(
    0.299 * red ** 2 +
    0.587 * green ** 2 +
    0.114 * blue ** 2,
  )
}

export function backgroundMutedColor(muted: string, backdrop: string): string {
  // ponytail: brightness gates ignore dark ink on light covers; use pixel-area
  // weighting if mixed-light artwork exposes a false positive.
  return perceivedBrightness(backdrop) >= 220 && perceivedBrightness(muted) < 40
    ? backdrop
    : muted
}

// Accent controls favor white text unless a color is visibly light (especially
// yellow or pastel). This keeps saturated colors such as orange visually cohesive.
const lightForegroundThreshold = 165

export function primaryForeground(color: string): '#FFFFFF' | '#18181B' {
  return perceivedBrightness(color) >= lightForegroundThreshold ? '#18181B' : '#FFFFFF'
}
