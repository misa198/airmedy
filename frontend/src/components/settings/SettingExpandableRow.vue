<script setup lang="ts">
import SettingRow from './SettingRow.vue'

defineProps<{
  title: string
  description?: string
  expanded?: boolean
}>()
</script>

<template>
  <div>
    <SettingRow :title="title" :description="description">
      <slot name="control" />
    </SettingRow>
    <Transition name="expand">
      <div v-if="expanded" class="expand-wrap">
        <div
          class="mx-5 mb-5 p-4 rounded-xl bg-foreground/[0.03] border border-foreground/[0.06]">
          <slot name="expanded" />
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.expand-wrap {
  overflow: hidden;
}
.expand-enter-active,
.expand-leave-active {
  transition:
    max-height 0.25s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  max-height: 240px;
}
.expand-enter-from,
.expand-leave-to {
  max-height: 0;
  opacity: 0;
}
</style>
