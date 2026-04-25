import { computed, type Ref } from 'vue'

export interface LyricLine {
  text: string
  secondary?: string
  time: number
}

export interface PlainLine {
  primary: string
  secondary?: string
}

const LRC_PATTERN = /^\[(\d+):(\d+\.\d+)\]/m
const LINE_PATTERN = /^\[(\d+):(\d+\.\d+)\](.*)/
const BILINGUAL_SEP = /\s*\^\s*|\s*\/\s*/

function parseBilingual(text: string): { primary: string; secondary?: string } {
  const parts = text.split(BILINGUAL_SEP, 2)
  if (parts.length === 2 && parts[1].trim()) {
    return { primary: parts[0].trim(), secondary: parts[1].trim() }
  }
  return { primary: text.trim() }
}

export function useLyrics(lyrics: Ref<string | undefined>) {
  const isSynced = computed(() => !!lyrics.value && LRC_PATTERN.test(lyrics.value))

  const syncedLines = computed<LyricLine[]>(() => {
    if (!lyrics.value) return []
    return lyrics.value.split('\n').flatMap(line => {
      const match = line.match(LINE_PATTERN)
      if (!match) return []
      const minutes = parseInt(match[1], 10)
      const seconds = parseFloat(match[2])
      const { primary, secondary } = parseBilingual(match[3])
      return [{ text: primary, secondary, time: minutes * 60 + seconds }]
    })
  })

  const plainLines = computed<PlainLine[]>(() => {
    if (!lyrics.value) return []
    return lyrics.value
      .split('\n')
      .map(l => l.replace(/^\[(\d+):(\d+\.\d+)\]/, '').trim())
      .filter(l => l)
      .map(l => {
        const { primary, secondary } = parseBilingual(l)
        return { primary, secondary }
      })
  })

  return { isSynced, syncedLines, plainLines }
}
