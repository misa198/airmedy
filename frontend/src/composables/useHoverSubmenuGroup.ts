import { ref } from 'vue'

/**
 * Coordinates multiple FilterSubmenu siblings so only one is open at a time.
 * Switching between sibling rows is instant (no delay); the close delay only
 * applies when the pointer actually leaves the whole group (e.g. the diagonal
 * path from a row to its own flyout, or leaving the dropdown entirely).
 */
export function useHoverSubmenuGroup(closeDelayMs = 120) {
  const activeKey = ref<symbol | null>(null)
  let closeTimer: ReturnType<typeof setTimeout> | null = null

  function enter(key: symbol) {
    if (closeTimer) {
      clearTimeout(closeTimer)
      closeTimer = null
    }
    activeKey.value = key
  }

  function leave(key: symbol) {
    closeTimer = setTimeout(() => {
      if (activeKey.value === key) activeKey.value = null
    }, closeDelayMs)
  }

  return { activeKey, enter, leave }
}
