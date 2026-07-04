<script setup lang="ts">
import { ref, onMounted, onUnmounted, onActivated, computed, nextTick } from 'vue'

const props = defineProps<{
  items: { id: string }[]
  itemHeight?: number
  gap?: number
  minColumnWidth?: number
  squareItems?: boolean
  textAreaHeight?: number
  paddingX?: number
  paddingY?: number
}>()

const paddingX = computed(() => props.paddingX ?? 24)
const paddingY = computed(() => props.paddingY ?? 24)

const containerRef = ref<HTMLElement | null>(null)
const scrollerRef = ref<any>(null)
const lastScrollTop = ref(0)
const containerWidth = ref(0)
const columns = ref(2)

const itemHeight = computed(() => props.itemHeight || 280) 
const gap = computed(() => props.gap || 24)
const minWidth = computed(() => props.minColumnWidth || 160)

const updateColumns = (anchor = false) => {
  if (!containerRef.value) return

  // Anchor to the top-most visible item so a width change (which alters both
  // column count and row height) doesn't make content appear to scroll away.
  const el = scrollerRef.value?.$el as HTMLElement | undefined
  const oldItemSize = totalItemHeight.value
  const oldColumns = columns.value
  const scrollTop = el ? el.scrollTop : 0
  // Only anchor when actually scrolled into the rows; while inside the top
  // padding zone keep the scroll position as-is so the top whitespace shows.
  const shouldAnchor = anchor && el != null && oldItemSize > 0 && scrollTop > paddingY.value
  const firstRow = shouldAnchor ? Math.floor((scrollTop - paddingY.value) / oldItemSize) : 0
  const firstItemIndex = firstRow * oldColumns

  containerWidth.value = containerRef.value.clientWidth
  const calculatedCols = Math.max(1, Math.floor((containerWidth.value + gap.value) / (minWidth.value + gap.value)))
  columns.value = calculatedCols

  if (shouldAnchor) {
    nextTick(() => {
      const newRow = Math.floor(firstItemIndex / columns.value)
      el!.scrollTop = paddingY.value + newRow * totalItemHeight.value
      lastScrollTop.value = el!.scrollTop
    })
  }
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
      updateColumns(true)
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
  let rowIndex = 0
  for (let i = 0; i < props.items.length; i += columns.value) {
    const chunk = props.items.slice(i, i + columns.value)
    result.push({
      // Stable row index as key: when column count changes the grouping shifts,
      // but keeping ids 0..n lets RecycleScroller reuse DOM nodes instead of
      // tearing down its pool (which causes a blank flash).
      id: String(rowIndex++),
      items: chunk
    })
  }
  return result
})

const totalItemHeight = computed(() => {
  if (props.squareItems && containerWidth.value && columns.value) {
    const COLUMN_GAP = 24 // gap-6 hardcoded in template
    const rowPadding = paddingX.value * 2 // horizontal padding both sides
    const cardWidth = (containerWidth.value - rowPadding - (columns.value - 1) * COLUMN_GAP) / columns.value
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
      @scroll.passive="handleScroll"
    >
      <template #default="{ item: row }">
        <div
          class="grid gap-6"
          :style="{
            gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
            paddingLeft: `${paddingX}px`,
            paddingRight: `${paddingX}px`,
            paddingBottom: `${gap}px`
          }"
        >
          <div v-for="item in row.items" :key="item.id">
            <slot :item="item"></slot>
          </div>
        </div>
      </template>
      <template #before>
        <div :style="{ height: `${paddingY}px` }" />
      </template>
      <template #after>
        <div :style="{ height: `${paddingY}px` }" />
      </template>
    </RecycleScroller>
  </div>
</template>

<style scoped>
</style>
