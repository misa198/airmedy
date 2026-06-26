<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  expanded?: boolean
}>()
</script>

<template>
  <div>
    <div class="p-5 flex items-center justify-between gap-x-2">
      <div>
        <p class="text-sm font-semibold">{{ title }}</p>
        <p v-if="description" class="text-xs text-foreground opacity-60 mt-1">{{ description }}</p>
      </div>
      <slot name="control" />
    </div>
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
