<script setup lang="ts" generic="T extends { id: string }">
import { ref, onMounted, onUnmounted, computed } from 'vue'

const props = defineProps<{
  items: T[]
  itemHeight?: number
  gap?: number
  minColumnWidth?: number
}>()

const containerRef = ref<HTMLElement | null>(null)
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

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})

const rows = computed(() => {
  const result: { id: string; items: T[] }[] = []
  for (let i = 0; i < props.items.length; i += columns.value) {
    const chunk = props.items.slice(i, i + columns.value)
    result.push({
      id: chunk[0].id, // Use first item ID as row ID
      items: chunk
    })
  }
  return result
})

const totalItemHeight = computed(() => itemHeight.value + gap.value)
</script>

<template>
  <div ref="containerRef" class="h-full w-full overflow-hidden">
    <RecycleScroller
      v-if="columns > 0"
      class="h-full w-full"
      :items="rows"
      :item-size="totalItemHeight"
      key-field="id"
      v-slot="{ item: row }"
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
.vue-recycle-scroller {
  scrollbar-width: thin;
}
</style>
