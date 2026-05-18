<script setup lang="ts">
import { ref, computed, onActivated } from 'vue'
import { Search, Play } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'
import { foldUnicode } from '@/lib/utils'

const props = defineProps<{
  title: string
  items: { id: string; name: string }[]
  isLoading?: boolean
  selectedId?: string
  searchPlaceholder?: string
  icon?: any
}>()

const emit = defineEmits<{
  'select': [id: string]
  'play': [item: { id: string; name: string }]
}>()

const searchQuery = ref('')
const scrollerRef = ref<any>(null)
const lastScrollTop = ref(0)

const handleScroll = (event: Event) => {
  const target = event.target as HTMLElement
  if (target) {
    lastScrollTop.value = target.scrollTop
  }
}

onActivated(() => {
  if (scrollerRef.value && lastScrollTop.value > 0) {
    setTimeout(() => {
      if (scrollerRef.value && scrollerRef.value.$el) {
        scrollerRef.value.$el.scrollTop = lastScrollTop.value
      }
    }, 0)
  }
})

const filteredItems = computed(() => {
  if (!searchQuery.value) return props.items
  const query = foldUnicode(searchQuery.value)
  return props.items.filter(item =>
    foldUnicode(item.name || '').includes(query)
  )
})
</script>

<template>
  <div class="h-full flex overflow-hidden bg-background">
    <!-- Left Column: Navigation List -->
    <div class="w-[280px] border-r border-foreground/[0.06] flex flex-col overflow-hidden bg-background select-none">
      <div class="p-6 pb-4">
        <h1 class="text-2xl font-bold mb-4">{{ title }}</h1>
        <div class="relative">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground opacity-60" />
          <Input v-model="searchQuery" type="text" :placeholder="searchPlaceholder || `${$t('sidebar.search')}...`"
            class="pl-10 pr-4" clearable />
        </div>
      </div>

      <div class="flex-1 overflow-hidden">
        <div v-if="isLoading" class="h-full flex items-center justify-center">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
        </div>

        <RecycleScroller v-else ref="scrollerRef" class="h-full px-3" :items="filteredItems" :item-size="56"
          key-field="id" v-slot="{ item }" @scroll.passive="handleScroll">
          <div @click="emit('select', item.id)" :class="[
            'flex items-center gap-3 p-2 rounded-lg group transition-colors cursor-pointer mb-1',
            selectedId === item.id ? 'bg-foreground/[0.08] text-foreground font-medium' : 'hover:bg-foreground/[0.04]'
          ]">
            <div :class="[
              'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ring-1 transition-colors overflow-hidden',
              selectedId === item.id ? 'bg-foreground/10 ring-foreground/[0.12]' : 'bg-foreground/5 ring-foreground/[0.06] group-hover:ring-foreground/[0.12]'
            ]">
              <slot name="item-icon" :item="item">
                <component :is="icon" v-if="icon" class="w-4 h-4" />
                <span v-else class="text-xs font-bold">{{ item.name.charAt(0).toUpperCase() }}</span>
              </slot>
            </div>
            <div class="flex-1 truncate font-medium">{{ item.name || $t('library.unknown') }}</div>
            <button @click.stop="emit('play', item)"
              class="p-1.5 opacity-0 group-hover:opacity-100 bg-foreground text-background rounded-full shadow-lg transition-all scale-90 hover:scale-100">
              <Play class="w-3 h-3 fill-current" />
            </button>
          </div>
        </RecycleScroller>
      </div>
    </div>

    <!-- Right Column: Detail View -->
    <div class="flex-1 overflow-hidden bg-background relative">
      <slot v-if="selectedId"></slot>
      <div v-if="!selectedId && !isLoading"
        class="h-full flex flex-col items-center justify-center text-foreground opacity-60 animate-in fade-in duration-500">
        <component :is="icon" v-if="icon" class="w-16 h-16 mb-4 opacity-10" />
        <p class="text-lg">{{ $t('library.select_item') }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
</style>
