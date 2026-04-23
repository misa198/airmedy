<script setup lang="ts" generic="T extends { id: string; name: string }">
import { ref, computed } from 'vue'
import { Search, Play } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'

const props = defineProps<{
  title: string
  items: T[]
  isLoading?: boolean
  selectedId?: string
  searchPlaceholder?: string
  icon?: any
}>()

const emit = defineEmits<{
  'select': [id: string]
  'play': [item: T]
}>()

const searchQuery = ref('')

const filteredItems = computed(() => {
  if (!searchQuery.value) return props.items
  const query = searchQuery.value.toLowerCase()
  return props.items.filter(item => 
    (item.name || '').toLowerCase().includes(query)
  )
})
</script>

<template>
  <div class="h-full flex overflow-hidden bg-background">
    <!-- Left Column: Navigation List -->
    <div class="w-64 md:w-80 border-r border-white/[0.06] flex flex-col overflow-hidden bg-background">
      <div class="p-6 pb-4">
        <h1 class="text-2xl font-bold mb-4">{{ title }}</h1>
        <div class="relative">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/40" />
          <Input
            v-model="searchQuery"
            type="text"
            :placeholder="searchPlaceholder || 'Search...'"
            class="pl-10 pr-4"
          />
        </div>
      </div>

      <div class="flex-1 overflow-hidden">
        <div v-if="isLoading" class="h-full flex items-center justify-center">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
        </div>
        
        <RecycleScroller
          v-else
          class="h-full px-3"
          :items="filteredItems"
          :item-size="56"
          key-field="id"
          v-slot="{ item }"
        >
          <div
            @click="emit('select', item.id)"
            :class="[
              'flex items-center gap-3 p-2 rounded-lg group transition-colors cursor-pointer mb-1',
              selectedId === item.id ? 'bg-white/[0.08] text-white font-medium' : 'hover:bg-white/[0.04]'
            ]"
          >
            <div :class="[
              'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ring-1 transition-colors',
              selectedId === item.id ? 'bg-white/10 ring-white/[0.12]' : 'bg-white/5 ring-white/[0.06] group-hover:ring-white/[0.12]'
            ]">
              <component :is="icon" v-if="icon" class="w-4 h-4" />
              <span v-else class="text-xs font-bold">{{ item.name.charAt(0).toUpperCase() }}</span>
            </div>
            <div class="flex-1 truncate font-medium">{{ item.name || 'Unknown' }}</div>
            <button
              v-if="selectedId !== item.id"
              @click.stop="emit('play', item)"
              class="p-1.5 opacity-0 group-hover:opacity-100 bg-white text-black rounded-full shadow-lg transition-all scale-90 hover:scale-100"
            >
              <Play class="w-3 h-3 fill-current" />
            </button>
          </div>
        </RecycleScroller>
      </div>
    </div>

    <!-- Right Column: Detail View -->
    <div class="flex-1 overflow-hidden bg-background relative">
      <slot v-if="selectedId"></slot>
      <div v-if="!selectedId && !isLoading" class="h-full flex flex-col items-center justify-center text-white/40 animate-in fade-in duration-500">
        <component :is="icon" v-if="icon" class="w-16 h-16 mb-4 opacity-10" />
        <p class="text-lg">Select an item to view details</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.vue-recycle-scroller {
  scrollbar-width: thin;
}
</style>
