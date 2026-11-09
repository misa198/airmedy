<script setup lang="ts">
import { Search } from '@lucide/vue'
import { Input } from '@airmedy/ui'
import { useCustomTitleBar } from '@/composables/useCustomTitleBar';

const props = defineProps<{
  title: string
  searchPlaceholder?: string
}>()

const { hasCustomTitleBar } = useCustomTitleBar()
const searchQuery = defineModel<string>({ default: '' })
</script>

<template>
  <div class="px-6 pb-6 border-b border-foreground/[0.06] select-none" :class="hasCustomTitleBar ? 'pt-2' : 'pt-6'">
    <div class="flex items-center justify-between gap-4">
      <h1 class="text-3xl font-bold shrink-0">{{ title }}</h1>
      <div class="flex items-center gap-2">
        <slot name="actions" />
        <div v-if="props.searchPlaceholder" class="relative max-w-sm w-full z-[99]">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-dim" />
          <Input v-model="searchQuery" type="text" :placeholder="searchPlaceholder" class="pl-10 pr-4" clearable />
        </div>
      </div>
    </div>
  </div>
</template>
