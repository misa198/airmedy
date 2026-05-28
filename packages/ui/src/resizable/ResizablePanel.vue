<script setup lang="ts">
import { Panel } from 'vue-resizable-panels'
import type { PanelProps } from 'vue-resizable-panels'

const props = defineProps<PanelProps>()
</script>

<template>
  <Panel v-bind="props">
    <slot />
  </Panel>
</template>
