<script setup lang="ts">
import { SelectIcon, SelectTrigger, type SelectTriggerProps, useForwardProps } from 'radix-vue'
import { ChevronDown } from '@lucide/vue'
import { cn } from '@airmedy/utils'

const props = defineProps<SelectTriggerProps & { class?: any }>()

const forwardedProps = useForwardProps(props)
</script>

<template>
  <SelectTrigger
    v-bind="forwardedProps"
    :class="cn(
      'flex h-10 w-full items-center justify-between rounded-2xl border bg-foreground/[0.07] border-foreground/20 px-3 py-2 text-sm ring-offset-background placeholder:text-foreground opacity-60 focus:outline-none focus:ring-2 focus:ring-foreground/20 disabled:cursor-not-allowed disabled:opacity-50 [&>span]:line-clamp-1',
      props.class,
    )"
  >
    <slot />
    <SelectIcon as-child>
      <ChevronDown class="h-4 w-4 opacity-50" />
    </SelectIcon>
  </SelectTrigger>
</template>
