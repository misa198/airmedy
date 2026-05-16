<script setup lang="ts">
import { SelectGroup, type SelectGroupProps } from 'radix-vue'
import { cn } from '@/lib/utils'
import { computed } from 'vue'

const props = defineProps<SelectGroupProps & { class?: any }>()
const forwarded = computed(() => {
  const { class: _, ...rest } = props
  return rest
})
</script>

<template>
  <SelectGroup :class="cn('p-1 w-full', props.class)" v-bind="forwarded">
    <slot />
  </SelectGroup>
</template>
