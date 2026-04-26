import { ref } from 'vue'
import type { Component } from 'vue'

export interface ContextMenuItem {
  label?: string
  icon?: Component
  iconRight?: Component
  action?: () => void
  children?: ContextMenuItem[]
  danger?: boolean
  disabled?: boolean
  separator?: boolean
}

export function useContextMenu() {
  const visible = ref(false)
  const x = ref(0)
  const y = ref(0)
  const items = ref<ContextMenuItem[]>([])

  function open(e: MouseEvent, menuItems: ContextMenuItem[]) {
    e.preventDefault()
    e.stopPropagation()
    x.value = e.clientX
    y.value = e.clientY
    items.value = menuItems
    visible.value = true
  }

  function close() {
    visible.value = false
  }

  return { visible, x, y, items, open, close }
}
