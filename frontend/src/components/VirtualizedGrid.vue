<script setup lang="ts">
import { ref, onMounted, onUnmounted, onActivated, computed } from 'vue'

const props = defineProps<{
  items: { id: string }[]
  itemHeight?: number
  gap?: number
  minColumnWidth?: number
  squareItems?: boolean
  textAreaHeight?: number
}>()

const containerRef = ref<HTMLElement | null>(null)
const scrollerRef = ref<any>(null)
const lastScrollTop = ref(0)
const containerWidth = ref(0)
const columns = ref(2)

const itemHeight = computed(() => props.itemHeight || 280) 
const gap = computed(() => props.gap || 24)
const minWidth = computed(() => props.minColumnWidth || 160)

const updateColumns = () => {
  if (!containerRef.value) return
  containerWidth.value = containerRef.value.clientWidth
  const calculatedCols = Math.max(1, Math.floor((containerWidth.value + gap.value) / (minWidth.value + gap.value)))
  columns.value = calculatedCols
}

const handleScroll = (event: Event) => {
  const target = event.target as HTMLElement
  if (target) {
    lastScrollTop.value = target.scrollTop
  }
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  updateColumns()
  if (containerRef.value) {
    resizeObserver = new ResizeObserver(() => {
      updateColumns()
    })
    resizeObserver.observe(containerRef.value)
  }
})

onActivated(() => {
  if (scrollerRef.value && lastScrollTop.value > 0) {
    // Small timeout to ensure the scroller has initialized after being re-attached to DOM
    setTimeout(() => {
      if (scrollerRef.value && scrollerRef.value.$el) {
        scrollerRef.value.$el.scrollTop = lastScrollTop.value
      }
    }, 0)
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})

const rows = computed(() => {
  const result: { id: string; items: { id: string }[] }[] = []
  for (let i = 0; i < props.items.length; i += columns.value) {
    const chunk = props.items.slice(i, i + columns.value)
    result.push({
      id: chunk[0].id, // Use first item ID as row ID
      items: chunk
    })
  }
  return result
})

const totalItemHeight = computed(() => {
  if (props.squareItems && containerWidth.value && columns.value) {
    const COLUMN_GAP = 24 // gap-6 hardcoded in template
    const ROW_PADDING = 8 // px-1 (4px each side) hardcoded in template
    const cardWidth = (containerWidth.value - ROW_PADDING - (columns.value - 1) * COLUMN_GAP) / columns.value
    return Math.ceil(cardWidth) + (props.textAreaHeight ?? 0) + gap.value
  }
  return itemHeight.value + gap.value
})
</script>

<template>
  <div ref="containerRef" class="h-full w-full overflow-hidden">
    <RecycleScroller
      v-if="columns > 0"
      ref="scrollerRef"
      class="h-full w-full"
      :items="rows"
      :item-size="totalItemHeight"
      key-field="id"
      v-slot="{ item: row }"
      @scroll.passive="handleScroll"
    >
      <div 
        class="grid gap-6 px-1" 
        :style="{ 
          gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
          paddingBottom: `${gap}px`
        }"
      >
        <div v-for="item in row.items" :key="item.id">
          <slot :item="item"></slot>
        </div>
      </div>
    </RecycleScroller>
  </div>
</template>

<style scoped>
</style>
