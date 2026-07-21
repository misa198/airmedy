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

export function primaryForeground(color: string): '#000000' | '#FFFFFF' {
  const [red, green, blue] = hexToRgbChannels(color).split(' ').map(Number)
  const luminance = [red, green, blue]
    .map(channel => {
      const value = channel / 255
      return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
    })
    .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0)
  return luminance > 0.179 ? '#000000' : '#FFFFFF'
}
