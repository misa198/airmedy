<script setup lang="ts">
import { type HTMLAttributes, computed } from 'vue'
import {
  PanelGroup,
  type PanelGroupProps,
} from 'vue-resizable-panels'
import { useForwardPropsEmits } from 'radix-vue'
import { cn } from '@/lib/utils'

interface ResizablePanelGroupProps extends PanelGroupProps {
  class?: HTMLAttributes['class']
}

const props = defineProps<ResizablePanelGroupProps>()
const emits = defineEmits<{
  (e: 'layout', sizes: number[]): void
}>()

const delegatedProps = computed(() => {
  const { class: _, ...delegated } = props
  return delegated
})

const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <PanelGroup
    v-bind="forwarded"
    :class="
      cn(
        'flex h-full w-full data-[panel-group-direction=vertical]:flex-col',
        props.class,
      )
    "
  >
    <slot />
  </PanelGroup>
</template>
