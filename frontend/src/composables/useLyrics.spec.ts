import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useLyrics } from './useLyrics'

const parse = (content: string) => useLyrics(ref(content))

describe('Enhanced LRC', () => {
  it('uses the line timestamp for the first word and preserves Vietnamese and spaces', () => {
    const { syncedLines, plainLines } = parse('[00:01.62]Người <00:01.79>hỏi <00:02.05>anh\u00a0<00:02.30>rằng\n[00:02.58]')
    expect(syncedLines.value[0]).toEqual({
      time: 1.62, text: 'Người hỏi anh\u00a0rằng', secondary: undefined,
      words: [
        { text: 'Người ', start: 1.62, end: 1.79 },
        { text: 'hỏi ', start: 1.79, end: 2.05 },
        { text: 'anh\u00a0', start: 2.05, end: 2.3 },
        { text: 'rằng', start: 2.3, end: 2.58 },
      ],
    })
    expect(syncedLines.value[1].text).toBe('')
    expect(plainLines.value).toEqual([{ primary: 'Người hỏi anh\u00a0rằng', secondary: undefined }])
  })

  it('honors explicit end markers even beyond the one second fallback', () => {
    const { syncedLines } = parse('[00:01]<00:01.0>Hello <00:02.123>world<00:06.50>\n[00:10]Next')
    expect(syncedLines.value[0].words).toEqual([
      { text: 'Hello ', start: 1, end: 2.123 }, { text: 'world', start: 2.123, end: 6.5 },
    ])
    expect(syncedLines.value[1].words).toBeUndefined()
  })

  it('caps missing ends at one second, including the last line', () => {
    const { syncedLines } = parse('[00:01]one <00:02>two\n[00:20]three <00:21>four')
    expect(syncedLines.value.map(line => line.words?.at(-1)?.end)).toEqual([3, 22])
  })

  it('preserves spaces between an explicit word end and the next word start', () => {
    const { syncedLines } = parse('[00:01]<00:01>Hello<00:02> <00:03>world<00:04>')
    expect(syncedLines.value[0].words).toEqual([
      { text: 'Hello ', start: 1, end: 2 }, { text: 'world', start: 3, end: 4 },
    ])
  })

  it('cleans inline tags before bilingual and romanization consumers see the text', () => {
    const { syncedLines, plainLines } = parse('[00:01]你<00:02>好<00:03> ^ Xin <00:02>chào')
    expect(syncedLines.value[0].text).toBe('你好')
    expect(syncedLines.value[0].secondary).toBe('Xin chào')
    expect(syncedLines.value[0].words?.at(-1)?.end).toBe(3)
    expect(plainLines.value[0]).toEqual({ primary: '你好', secondary: 'Xin chào' })
  })

  it.each(['<00:00.5>', '<00:61>', '<00:bad>', '<00:02.1234>'])('falls back on invalid timing %s', marker => {
    const { syncedLines } = parse(`[00:01]one ${marker}two`)
    expect(syncedLines.value[0].words).toBeUndefined()
    expect(syncedLines.value[0].text).toBe('one two')
  })

  it('accepts equal timestamps and decodes entities without interpreting HTML', () => {
    const { syncedLines } = parse('[00:01]A &lt;00:01&gt;B &amp; C<00:02>')
    expect(syncedLines.value[0].words).toEqual([
      { text: 'A ', start: 1, end: 1 }, { text: 'B & C', start: 1, end: 2 },
    ])
  })

  it('retains ordinary LRC and plain lyrics, and reparses a changed track', () => {
    const content = ref('[00:01.00]First / Translation\n[00:02.00]Second')
    const lyrics = useLyrics(content)
    expect(lyrics.isSynced.value).toBe(true)
    expect(lyrics.syncedLines.value[0]).toEqual({ time: 1, text: 'First', secondary: 'Translation' })
    content.value = 'Plain lyrics'
    expect(lyrics.isSynced.value).toBe(false)
    expect(lyrics.syncedLines.value).toEqual([])
    expect(lyrics.plainLines.value).toEqual([{ primary: 'Plain lyrics', secondary: undefined }])
  })
})
