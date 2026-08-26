import { describe, expect, it } from 'vitest'
import { backgroundMutedColor, hexToRgbChannels, normalizeHexColor, primaryForeground } from './color'

describe('color helpers', () => {
  it('normalizes valid hex colors and rejects invalid values', () => {
    expect(normalizeHexColor(' #ea580c ')).toBe('#EA580C')
    expect(normalizeHexColor('#F00')).toBeNull()
  })

  it('uses the default primary color when RGB input is invalid', () => {
    expect(hexToRgbChannels('invalid')).toBe('225 29 72')
  })

  it('uses white for saturated primary accents and dark ink for visibly light colors', () => {
    expect(primaryForeground('#EA580C')).toBe('#FFFFFF')
    expect(primaryForeground('#E11D48')).toBe('#FFFFFF')
    expect(primaryForeground('#FEF3C7')).toBe('#18181B')
  })

  it('ignores dark ink only when the overall artwork is light', () => {
    expect(backgroundMutedColor('#0A0A0A', '#F4E6E6')).toBe('#F4E6E6')
    expect(backgroundMutedColor('#0E2022', '#3E7863')).toBe('#0E2022')
  })
})
