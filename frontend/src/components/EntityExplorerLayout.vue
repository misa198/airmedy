<script setup lang="ts" generic="T extends { id: string; name: string }">
import { ref, computed } from 'vue'
import { Search, Play } from 'lucide-vue-next'

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
    <div class="w-64 md:w-80 border-r flex flex-col overflow-hidden bg-muted/10">
      <div class="p-6 pb-4">
        <h1 class="text-2xl font-bold mb-4">{{ title }}</h1>
        <div class="relative">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input 
            v-model="searchQuery"
            type="text" 
            :placeholder="searchPlaceholder || 'Search...'" 
            class="w-full bg-accent/50 border rounded-md pl-10 pr-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
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
              selectedId === item.id ? 'bg-primary text-primary-foreground shadow-sm' : 'hover:bg-accent/50'
            ]"
          >
            <div :class="[
              'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 border transition-colors',
              selectedId === item.id ? 'bg-primary-foreground/10 border-primary-foreground/20' : 'bg-muted group-hover:border-primary/50'
            ]">
              <component :is="icon" v-if="icon" class="w-4 h-4" />
              <span v-else class="text-xs font-bold">{{ item.name.charAt(0).toUpperCase() }}</span>
            </div>
            <div class="flex-1 truncate font-medium">{{ item.name || 'Unknown' }}</div>
            <button 
              v-if="selectedId !== item.id"
              @click.stop="emit('play', item)"
              class="p-1.5 opacity-0 group-hover:opacity-100 bg-primary text-primary-foreground rounded-full shadow-lg transition-all scale-90 hover:scale-100"
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
      <div v-if="!selectedId && !isLoading" class="h-full flex flex-col items-center justify-center text-muted-foreground animate-in fade-in duration-500">
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
