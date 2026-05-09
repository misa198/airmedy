<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { ChevronLeft, ChevronRight, Play } from 'lucide-vue-next'

const props = defineProps<{
  title: string
  icon: any
  items: any[]
  id: string
}>()

const emit = defineEmits<{
  'play-all': []
}>()

const sectionRef = ref<HTMLElement | null>(null)
const columnsPerPage = ref(5)
const currentPage = ref(0)
const transitionName = ref('slide-next')

// 2 rows layout
const itemsPerPage = computed(() => columnsPerPage.value * 2)
const totalPages = computed(() => Math.ceil(props.items.length / itemsPerPage.value))

const paginatedItems = computed(() => {
  // Ensure we never start so late that we don't have enough items to fill the page
  const maxStart = Math.max(0, props.items.length - itemsPerPage.value)
  const start = Math.min(currentPage.value * itemsPerPage.value, maxStart)
  return props.items.slice(start, start + itemsPerPage.value)
})

const updateColumns = () => {
  if (sectionRef.value) {
    const width = sectionRef.value.offsetWidth
    // Item width (180px) + Gap (24px)
    const itemFullWidth = 180 + 24
    // We add one gap back to the total width because the last item doesn't need a right gap
    const cols = Math.floor((width + 24) / itemFullWidth)
    columnsPerPage.value = Math.max(1, cols)
  }
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  updateColumns()
  if (sectionRef.value) {
    resizeObserver = new ResizeObserver(updateColumns)
    resizeObserver.observe(sectionRef.value)
  }
})

onUnmounted(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
  }
})

// Reset to first page if items change significantly
watch(() => props.items.length, () => {
  currentPage.value = 0
})

// Ensure we don't stay on a page that no longer exists after resize
watch(totalPages, (newTotal) => {
  if (currentPage.value >= newTotal && newTotal > 0) {
    currentPage.value = newTotal - 1
  }
})

const next = () => {
  if (currentPage.value < totalPages.value - 1) {
    transitionName.value = 'slide-next'
    currentPage.value++
  }
}

const prev = () => {
  if (currentPage.value > 0) {
    transitionName.value = 'slide-prev'
    currentPage.value--
  }
}
</script>

<template>
  <section ref="sectionRef" v-if="items.length > 0" class="animate-in fade-in slide-in-from-bottom-4 duration-700">
    <div class="flex items-center justify-between mb-6">
      <div class="flex items-center gap-3 select-none">
        <div class="p-2 bg-primary/10 rounded-lg">
          <component :is="icon" class="w-5 h-5 text-primary" />
        </div>
        <div class="flex items-center gap-3">
          <h2 class="text-2xl font-bold tracking-tight">{{ title }}</h2>
          <button 
            @click="emit('play-all')"
            class="group flex items-center gap-2 px-3 py-1 bg-foreground/5 hover:bg-primary hover:text-primary-foreground rounded-full text-xs font-semibold transition-all"
          >
            <Play class="w-3 h-3 fill-current" />
            <span>{{ $t('common.play') }}</span>
          </button>
        </div>
      </div>
      
      <div v-if="totalPages > 1" class="flex gap-2">
        <button 
          @click="prev"
          :disabled="currentPage === 0"
          class="p-2 rounded-full bg-foreground/5 hover:bg-foreground/10 disabled:opacity-20 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronLeft class="w-5 h-5" />
        </button>
        <button 
          @click="next"
          :disabled="currentPage === totalPages - 1"
          class="p-2 rounded-full bg-foreground/5 hover:bg-foreground/10 disabled:opacity-20 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronRight class="w-5 h-5" />
        </button>
      </div>
    </div>
    
    <div class="relative overflow-hidden">
      <Transition :name="transitionName">
        <div 
          :key="currentPage"
          class="grid gap-x-6 gap-y-8 w-full"
          :style="{
            gridTemplateColumns: `repeat(${columnsPerPage}, minmax(0, 1fr))`
          }"
        >
          <div 
            v-for="(item, index) in paginatedItems" 
            :key="item.id || index"
          >
            <slot :item="item" :index="index"></slot>
          </div>
        </div>
      </Transition>
    </div>
  </section>
</template>

<style scoped>
.slide-next-enter-active,
.slide-next-leave-active,
.slide-prev-enter-active,
.slide-prev-leave-active {
  transition: transform 0.5s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-next-enter-from {
  transform: translateX(50px);
  opacity: 0;
}
.slide-next-leave-to {
  transform: translateX(-50px);
  opacity: 0;
}

.slide-prev-enter-from {
  transform: translateX(-50px);
  opacity: 0;
}
.slide-prev-leave-to {
  transform: translateX(50px);
  opacity: 0;
}

.slide-next-leave-active,
.slide-prev-leave-active {
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}
</style>
