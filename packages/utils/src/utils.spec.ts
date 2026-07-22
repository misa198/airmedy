import { describe, expect, it } from 'vitest'
import { decodeHTMLEntities, foldUnicode, formatTime, hexToRgba, getTrackDisplayTitle, formatTotalDuration } from './utils'

describe('utils', () => {
  describe('decodeHTMLEntities', () => {
    it('decodes common HTML entities correctly', () => {
      expect(decodeHTMLEntities('don&apos;t')).toBe("don't")
      expect(decodeHTMLEntities('d&apos;accord')).toBe("d'accord")
      expect(decodeHTMLEntities('she said &quot;hello&quot;')).toBe('she said "hello"')
      expect(decodeHTMLEntities('A &amp; B')).toBe('A & B')
      expect(decodeHTMLEntities('10 &lt; 20')).toBe('10 < 20')
      expect(decodeHTMLEntities('20 &gt; 10')).toBe('20 > 10')
      expect(decodeHTMLEntities('hello&nbsp;world')).toBe('hello world')
    })

    it('decodes numeric entities correctly', () => {
      expect(decodeHTMLEntities('&#39;')).toBe("'")
      expect(decodeHTMLEntities('&#x27;')).toBe("'")
      expect(decodeHTMLEntities('&#x22;')).toBe('"')
      expect(decodeHTMLEntities('&#60;')).toBe('<')
      expect(decodeHTMLEntities('&#x3E;')).toBe('>')
    })

    it('handles empty, null, or undefined strings gracefully', () => {
      expect(decodeHTMLEntities('')).toBe('')
      expect(decodeHTMLEntities(null as any)).toBe('')
      expect(decodeHTMLEntities(undefined as any)).toBe('')
    })

    it('leaves normal text unchanged', () => {
      expect(decodeHTMLEntities('hello world')).toBe('hello world')
      expect(decodeHTMLEntities("don't change me")).toBe("don't change me")
    })

    it('retains unmapped or invalid entities as is', () => {
      expect(decodeHTMLEntities('&unknown;')).toBe('&unknown;')
      expect(decodeHTMLEntities('&#invalid;')).toBe('&#invalid;')
      expect(decodeHTMLEntities('&#xinvalid;')).toBe('&#xinvalid;')
    })
  })

  describe('formatTime', () => {
    it('formats positive numbers correctly', () => {
      expect(formatTime(0)).toBe('0:00')
      expect(formatTime(5)).toBe('0:05')
      expect(formatTime(65)).toBe('1:05')
      expect(formatTime(3599)).toBe('59:59')
    })

    it('handles negative or invalid values', () => {
      expect(formatTime(-1)).toBe('0:00')
      expect(formatTime(NaN)).toBe('0:00')
      expect(formatTime(Infinity)).toBe('0:00')
    })
  })

  describe('hexToRgba', () => {
    it('converts hex to rgba', () => {
      expect(hexToRgba('#ffffff', 0.5)).toBe('rgba(255, 255, 255, 0.5)')
      expect(hexToRgba('000000', 1)).toBe('rgba(0, 0, 0, 1)')
    })
  })

  describe('getTrackDisplayTitle', () => {
    it('returns track title if present', () => {
      expect(getTrackDisplayTitle({ title: 'My Track' })).toBe('My Track')
    })

    it('extracts filename from path if title is missing', () => {
      expect(getTrackDisplayTitle({ path: '/path/to/my_file.mp3' })).toBe('my_file')
      expect(getTrackDisplayTitle({ path: 'C:\\path\\to\\my_file.wav' })).toBe('my_file')
    })

    it('returns empty string if both title and path are missing', () => {
      expect(getTrackDisplayTitle({})).toBe('')
    })
  })

  describe('formatTotalDuration', () => {
    it('formats duration with hours and minutes, then days and hours', () => {
      const mockT = (key: string) => ({ 'common.day': 'd', 'common.hr': 'hr', 'common.min': 'min' })[key] || key
      expect(formatTotalDuration(3660, mockT)).toBe('1 hr 1 min')
      expect(formatTotalDuration(120, mockT)).toBe('2 min')
      expect(formatTotalDuration(52 * 86400 + 3 * 3600 + 30 * 60, mockT)).toBe('52 d 3 hr')
    })
  })

  describe('foldUnicode', () => {
    it('normalizes and folds unicode characters', () => {
      expect(foldUnicode('tiếng việt')).toBe('tieng viet')
      expect(foldUnicode('Đường')).toBe('duong')
      expect(foldUnicode('')).toBe('')
    })
  })
})
