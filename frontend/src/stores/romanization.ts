import { defineStore } from 'pinia'
import { ref } from 'vue'

// Session preference only. Results belong to the current mounted lyric surface.
export const useRomanizationStore = defineStore('romanization', () => {
  const enabled = ref(false)
  return { enabled }
})
