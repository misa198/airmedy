<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
  noTitleWrap?: boolean
}>()
</script>

<template>
  <div class="w-full flex flex-col">
    <div class="p-5 flex items-center justify-between gap-x-2">
      <div
        v-if="$slots.leading"
        class="flex items-center gap-4"
        :class="noTitleWrap && 'min-w-0 flex-1'"
      >
        <slot name="leading" />
        <div :class="noTitleWrap && 'min-w-0'">
          <p class="text-sm font-semibold" :class="noTitleWrap && 'truncate'">
            <slot name="title">{{ title }}</slot>
          </p>
          <div v-if="description || $slots.description"
            class="text-xs text-dim mt-1 whitespace-pre-line">
            <slot name="description">{{ description }}</slot>
          </div>
        </div>
      </div>
      <div v-else :class="noTitleWrap && 'min-w-0 flex-1'">
        <p class="text-sm font-semibold" :class="noTitleWrap && 'truncate'">
          <slot name="title">{{ title }}</slot>
        </p>
        <div v-if="description || $slots.description"
          class="text-xs text-dim mt-1 whitespace-pre-line">
          <slot name="description">{{ description }}</slot>
        </div>
      </div>
      <slot />
    </div>
    <slot name="content" />
  </div>
</template>
