import { computed, onUnmounted, ref, shallowRef, watch, type Ref } from 'vue'
import { LyricsService } from '../../bindings/airmedy/internal/infra/wails'
import { useRomanizationStore } from '../stores/romanization'

export function useRomanization(lines: Ref<string[]>, available: Ref<boolean>) {
  const store = useRomanizationStore()
  const supported = ref(false)
  const mandarinDefault = ref(false)
  const loading = ref(false)
  const error = ref(false)
  const result = shallowRef<(string | undefined)[]>([])
  let generation = 0
  let cancel: (() => void) | undefined
  let inspectionGeneration = 0
  let cancelInspection: (() => void) | undefined

  function invalidate() {
    generation++
    cancel?.()
    cancel = undefined
    loading.value = false
  }

  async function convert() {
    invalidate()
    if (!available.value || !supported.value || !store.enabled) return
    const current = generation
    loading.value = true
    error.value = false
    const request = LyricsService.RomanizeLyrics(lines.value)
    cancel = () => { void request.cancel().catch(() => {}) }
    try {
      const converted = await request
      if (current !== generation) return
      result.value = converted.map(line => line.status === 'converted' ? line.text : undefined)
      error.value = converted.some(line => line.status === 'failed')
    } catch {
      if (current === generation) error.value = true
    } finally {
      if (current === generation) { loading.value = false; cancel = undefined }
    }
  }

  watch([lines, available], async () => {
    invalidate()
    cancelInspection?.()
    const current = ++inspectionGeneration
    result.value = []
    supported.value = false
    mandarinDefault.value = false
    error.value = false
    if (!available.value) return
    const request = LyricsService.InspectRomanization(lines.value)
    cancelInspection = () => { void request.cancel().catch(() => {}) }
    try {
      const inspection = await request
      if (current !== inspectionGeneration) return
      cancelInspection = undefined
      supported.value = inspection.supported
      mandarinDefault.value = inspection.mandarinDefault
      await convert()
    } catch {
      if (current === inspectionGeneration) error.value = true
    }
  }, { immediate: true })

  watch(() => store.enabled, () => {
    if (store.enabled) void convert()
    else invalidate()
  })
  onUnmounted(() => {
    invalidate()
    inspectionGeneration++
    cancelInspection?.()
  })

  function toggle() {
    void store.setEnabled(!store.enabled)
  }

  return {
    supported, mandarinDefault, loading, error, toggle, retry: convert,
    enabled: computed(() => store.enabled),
    secondary: computed(() => store.enabled ? result.value : []),
  }
}
