<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ChevronRight } from 'lucide-vue-next'
import type { ContextMenuItem } from '@/composables/useContextMenu'

const props = defineProps<{
  items: ContextMenuItem[]
  x: number
  y: number
  visible: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const menuEl = ref<HTMLElement | null>(null)
const submenuEl = ref<HTMLElement | null>(null)
const adjustedX = ref(props.x)
const adjustedY = ref(props.y)
const activeSubmenuIndex = ref<number | null>(null)
const submenuX = ref(0)
const submenuY = ref(0)

watch(
  [() => props.visible, () => props.x, () => props.y],
  async ([visible, x, y]) => {
    if (visible) {
      adjustedX.value = x
      adjustedY.value = y
      
      await nextTick()
      if (!menuEl.value) return
      
      const rect = menuEl.value.getBoundingClientRect()
      const vw = window.innerWidth
      const vh = window.innerHeight
      
      if (x + rect.width > vw) {
        adjustedX.value = x - rect.width
      }
      if (y + rect.height > vh) {
        adjustedY.value = y - rect.height
      }
    }
  },
  { immediate: true }
)

function handleItemClick(item: ContextMenuItem) {
  if (item.disabled || item.separator || item.children?.length) return
  item.action?.()
  emit('close')
}

async function handleMouseEnter(item: ContextMenuItem, index: number, e: MouseEvent) {
  if (item.children?.length) {
    activeSubmenuIndex.value = index
    const el = e.currentTarget as HTMLElement
    const rect = el.getBoundingClientRect()
    submenuX.value = rect.right
    submenuY.value = rect.top

    await nextTick()
    if (!submenuEl.value) return

    const submenuRect = submenuEl.value.getBoundingClientRect()
    const vw = window.innerWidth
    const vh = window.innerHeight

    // Check right edge
    if (rect.right + submenuRect.width > vw) {
      submenuX.value = rect.left - submenuRect.width
    }
    // Check bottom edge
    if (rect.top + submenuRect.height > vh) {
      submenuY.value = vh - submenuRect.height - 8
    }
  } else {
    activeSubmenuIndex.value = null
  }
}

function handleSubmenuItemClick(item: ContextMenuItem) {
  if (item.disabled || item.separator) return
  item.action?.()
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <template v-if="visible">
      <!-- Invisible overlay to catch outside clicks -->
      <div class="fixed inset-0 z-[998]" @click="emit('close')" @contextmenu.prevent="emit('close')" />

      <!-- Menu panel -->
      <div
        ref="menuEl"
        class="fixed z-[999] min-w-[180px] rounded-lg bg-[#1A1A1A] ring-1 ring-white/[0.08] shadow-2xl py-1 select-none"
        :style="{ left: adjustedX + 'px', top: adjustedY + 'px' }"
      >
        <template v-for="(item, index) in items" :key="index">
          <div
            v-if="item.separator"
            class="my-1 mx-2 border-t border-white/[0.06]"
          />
          <div
            v-else
            class="relative flex items-center gap-2.5 px-3 py-1.5 text-sm cursor-default transition-colors"
            :class="[
              item.disabled
                ? 'text-white/25 cursor-not-allowed'
                : item.danger
                  ? 'text-red-400/80 hover:text-red-400 hover:bg-white/[0.06]'
                  : 'text-white/70 hover:text-white hover:bg-white/[0.06]',
            ]"
            @click="handleItemClick(item)"
            @mouseenter="handleMouseEnter(item, index, $event)"
          >
            <component :is="item.icon" v-if="item.icon" class="w-3.5 h-3.5 shrink-0 opacity-70" />
            <span class="flex-1">{{ item.label }}</span>
            <ChevronRight v-if="item.children?.length" class="w-3.5 h-3.5 opacity-40 ml-auto" />
          </div>
        </template>
      </div>

      <!-- Submenu panel -->
      <div
        v-if="activeSubmenuIndex !== null && items[activeSubmenuIndex]?.children?.length"
        ref="submenuEl"
        class="fixed z-[1000] min-w-[160px] max-h-64 overflow-y-auto rounded-lg bg-[#1A1A1A] ring-1 ring-white/[0.08] shadow-2xl py-1 select-none"
        :style="{ left: submenuX + 'px', top: submenuY + 'px' }"
        @mouseleave="activeSubmenuIndex = null"
      >
        <div
          v-for="(child, ci) in items[activeSubmenuIndex]!.children"
          :key="ci"
          class="flex items-center gap-2.5 px-3 py-1.5 text-sm cursor-default transition-colors"
          :class="child.disabled ? 'text-white/25' : 'text-white/70 hover:text-white hover:bg-white/[0.06]'"
          @click="handleSubmenuItemClick(child)"
        >
          <component :is="child.icon" v-if="child.icon" class="w-3.5 h-3.5 shrink-0 opacity-70" />
          <span>{{ child.label }}</span>
        </div>
        <div v-if="!items[activeSubmenuIndex]!.children!.length" class="px-3 py-1.5 text-sm text-white/30">
          No playlists
        </div>
      </div>
    </template>
  </Teleport>
</template>
