import { defineStore } from 'pinia'
import { ref } from 'vue'
import { Events } from '@wailsio/runtime'
import * as LyricsService from '../../bindings/airmedy/internal/infra/wails/lyricsservice'

// Session preference only. Results belong to the current mounted lyric surface.
export const useRomanizationStore = defineStore('romanization', () => {
  const enabled = ref(false)
  let revision = 0
  let cachedLines: string[] = []
  let cachedResult: (string | undefined)[] | undefined
  const off = Events.On('lyrics:romanization-enabled', (event: Events.WailsEvent) => {
    revision++
    enabled.value = Boolean(event.data)
  })

  void (async () => {
    const requestRevision = revision
    try {
      const backendEnabled = await LyricsService.GetRomanizationEnabled()
      if (requestRevision === revision) enabled.value = backendEnabled
    } catch {
      // The control remains off until the backend is available.
    }
  })()

  async function setEnabled(value: boolean) {
    revision++
    enabled.value = value
    try {
      await LyricsService.SetRomanizationEnabled(value)
    } catch {
      // Keep the local choice until a later backend event/hydration resolves it.
    }
  }

  function getCached(lines: string[]) {
    return cachedResult && lines.length === cachedLines.length && lines.every((line, index) => line === cachedLines[index])
      ? [...cachedResult]
      : undefined
  }

  function cache(lines: string[], result: (string | undefined)[]) {
    cachedLines = [...lines]
    cachedResult = [...result]
  }

  function dispose() {
    off()
  }

  return { enabled, setEnabled, getCached, cache, dispose }
})
