import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import * as SearchService from '../../bindings/airmedy/internal/infra/wails/searchservice'
import type { SearchResultSet } from '../../bindings/airmedy/internal/infra/wails/models'

export const useSearchStore = defineStore('search', () => {
  const query = ref('')
  const results = shallowRef<SearchResultSet | null>(null)
  const loading = ref(false)

  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  async function search(q: string) {
    query.value = q

    if (debounceTimer) clearTimeout(debounceTimer)

    if (!q.trim()) {
      results.value = null
      loading.value = false
      return
    }

    loading.value = true

    debounceTimer = setTimeout(async () => {
      try {
        const res = await SearchService.Search(q.trim())
        results.value = res
      } catch (e) {
        console.error('Search failed', e)
        results.value = null
      } finally {
        loading.value = false
      }
    }, 300)
  }

  function clear() {
    query.value = ''
    results.value = null
    loading.value = false
    if (debounceTimer) clearTimeout(debounceTimer)
  }

  return { query, results, loading, search, clear }
})
