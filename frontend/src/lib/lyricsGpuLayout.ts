import type { LyricWord } from '../composables/useLyrics'

export function lyricAppearance(index: number, active: number, browsing: boolean, immersive: boolean, hovered: number) {
  if (browsing) return { blur: 0, alpha: 1, scale: 1 }
  if (index === active) return { blur: 0, alpha: 1, scale: immersive ? 1.06 : 1.03 }
  if (immersive) {
    const distance = Math.min(Math.abs(index - active), 3)
    return { blur: [0, 0.35, 1.25, 2][distance], alpha: [1, 0.25, 0.15, 0.1][distance], scale: 1 }
  }
  const past = index < active
  return {
    blur: past ? 0.5 : hovered === index ? 0 : 1,
    alpha: past ? (hovered === index ? 0.4 : 0.2) * 0.6 : (hovered === index ? 0.6 : 0.3) * 0.4,
    scale: 1,
  }
}

export function initialLyricAppearance(appearance: ReturnType<typeof lyricAppearance>) {
  return { ...appearance, scale: 1 }
}

export function wordProgress(word: LyricWord, position: number) {
  if (word.end === word.start) return position >= word.start ? 1 : 0
  return Math.max(0, Math.min(1, (position - word.start) / (word.end - word.start)))
}

export function fragmentFill(progress: number, offset: number, width: number, total: number) {
  return Math.max(0, Math.min(width, progress * total - offset))
}

export function karaokeBaseAlpha(rowAlpha: number) {
  return Math.min(1, 0.35 / rowAlpha)
}

export function brightLayerVisible(activeWord: boolean, width: number) {
  return activeWord && width > 0
}

export interface LyricFragment {
  text: string
  x: number
  y: number
  width: number
  height: number
  fontFamily: string
  fontSize: number
  fontWeight: string
  secondary: boolean
  word?: number
}

// Let browser shaping/wrapping handle CJK, combining marks and bilingual text.
// Only measure on layout changes, never on a playback-position update.
export function measureLyricFragments(row: HTMLElement): LyricFragment[] {
  const origin = row.getBoundingClientRect()
  const walker = document.createTreeWalker(row, NodeFilter.SHOW_TEXT)
  const words = [...row.querySelectorAll('.karaoke-segment')]
  const fragments: LyricFragment[] = []
  const segmenter = new Intl.Segmenter(undefined, { granularity: 'grapheme' })
  let node: Node | null
  while ((node = walker.nextNode())) {
    const parent = node.parentElement!
    const style = getComputedStyle(parent)
    const wordIndex = words.indexOf(parent.closest('.karaoke-segment')!)
    const secondary = !!parent.closest('[data-lyric-secondary]')
    const text = node.textContent ?? ''
    const range = document.createRange()
    let fragment: LyricFragment | undefined
    for (const { segment, index } of segmenter.segment(text)) {
      range.setStart(node, index)
      range.setEnd(node, index + segment.length)
      const rect = range.getBoundingClientRect()
      if (!rect.height) continue
      const x = rect.left - origin.left
      const y = rect.top - origin.top
      if (!fragment || Math.abs(fragment.y - y) > 1) {
        fragment = {
          text: segment, x, y, width: rect.width, height: rect.height,
          fontFamily: style.fontFamily, fontSize: parseFloat(style.fontSize), fontWeight: style.fontWeight, secondary,
          word: wordIndex < 0 ? undefined : wordIndex,
        }
        fragments.push(fragment)
      } else {
        fragment.text += segment
        const right = Math.max(fragment.x + fragment.width, x + rect.width)
        fragment.x = Math.min(fragment.x, x)
        fragment.width = right - fragment.x
      }
    }
  }
  return fragments
}
