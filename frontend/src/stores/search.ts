import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import * as SearchService from '../../bindings/airmedy/internal/infra/wails/searchservice'
import type { SearchResultSet } from '../../bindings/airmedy/internal/infra/wails/models'

export const useSearchStore = defineStore('search', () => {
  const query = ref('')
  const results = shallowRef<SearchResultSet | null>(null)
  const loading = ref(false)

  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let requestVersion = 0

  async function runSearch(q: string, version: number) {
    try {
      const res = await SearchService.Search(q)
      if (version === requestVersion) results.value = res
    } catch (e) {
      if (version === requestVersion) {
        console.error('Search failed', e)
        results.value = null
      }
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  async function search(q: string) {
    query.value = q

    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }

    const version = ++requestVersion

    if (!q.trim()) {
      results.value = null
      loading.value = false
      return
    }

    loading.value = true

    debounceTimer = setTimeout(() => {
      debounceTimer = null
      void runSearch(q.trim(), version)
    }, 300)
  }

  // Re-run the existing query after a library mutation without resetting the
  // visible results to a loading skeleton. It also supersedes any pending
  // debounced input request so an older response cannot overwrite fresh data.
  async function refresh() {
    const q = query.value.trim()
    if (!q) return

    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }

    const version = ++requestVersion
    await runSearch(q, version)
  }

  function clear() {
    query.value = ''
    results.value = null
    loading.value = false
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = null
    requestVersion++
  }

  return { query, results, loading, search, refresh, clear }
})
