<script setup lang="ts">
import { Search } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'

const props = defineProps<{
  title: string
  searchPlaceholder?: string
}>()

const searchQuery = defineModel<string>({ default: '' })
</script>

<template>
  <div class="p-6 pb-4 border-b border-foreground/[0.06] select-none">
    <div class="flex items-center justify-between gap-4">
      <h1 class="text-3xl font-bold shrink-0">{{ title }}</h1>
      <div v-if="props.searchPlaceholder" class="relative max-w-sm w-full">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground opacity-60" />
        <Input
          v-model="searchQuery"
          type="text"
          :placeholder="searchPlaceholder"
          class="pl-10 pr-4"
          clearable
        />
      </div>
    </div>
  </div>
</template>
