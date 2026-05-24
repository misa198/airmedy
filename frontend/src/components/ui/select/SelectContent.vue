<script setup lang="ts">
import {
  SelectContent,
  type SelectContentEmits,
  type SelectContentProps,
  SelectPortal,
  SelectViewport,
  useForwardPropsEmits,
} from 'radix-vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<SelectContentProps & { class?: any }>(),
  {
    position: 'popper',
  },
)
const emits = defineEmits<SelectContentEmits>()

const forwarded = useForwardPropsEmits(props, emits)
</script>

<template>
  <SelectPortal>
    <SelectContent
      v-bind="forwarded"
      :class="cn(
        'relative z-50 min-w-[8rem] overflow-hidden rounded-2xl border border-border-glass bg-glass-elevated backdrop-blur-xl text-foreground shadow-2xl transform-gpu isolate data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2 data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2',
        position === 'popper'
          && 'data-[side=bottom]:translate-y-2 data-[side=left]:-translate-x-2 data-[side=right]:translate-x-2 data-[side=top]:-translate-y-2',
        props.class,
      )"
    >
      <SelectViewport
        :class="cn('p-1.5', position === 'popper' && 'w-full min-w-[var(--radix-select-trigger-width)]')"
      >
        <div :class="cn('overflow-x-hidden overflow-y-auto select-scrollbar', position === 'popper' && 'max-h-[min(var(--radix-select-content-available-height),20rem)]')">
          <slot />
        </div>
      </SelectViewport>
    </SelectContent>
  </SelectPortal>
</template>

<style scoped>
.select-scrollbar::-webkit-scrollbar {
  width: 2px;
}
.select-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.select-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(161, 161, 170, 0.4);
  border-radius: 9999px;
}
.select-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(161, 161, 170, 0.6);
}
</style>
