<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<template>
  <div class="w-full flex flex-col">
    <div class="p-5 flex items-center justify-between gap-x-2">
      <div v-if="$slots.leading" class="flex items-center gap-4">
        <slot name="leading" />
        <div>
          <p class="text-sm font-semibold">
            <slot name="title">{{ title }}</slot>
          </p>
          <div v-if="description || $slots.description"
            class="text-xs text-foreground opacity-60 mt-1 whitespace-pre-line">
            <slot name="description">{{ description }}</slot>
          </div>
        </div>
      </div>
      <div v-else>
        <p class="text-sm font-semibold">
          <slot name="title">{{ title }}</slot>
        </p>
        <div v-if="description || $slots.description"
          class="text-xs text-foreground opacity-60 mt-1 whitespace-pre-line">
          <slot name="description">{{ description }}</slot>
        </div>
      </div>
      <slot />
    </div>
    <slot name="content" />
  </div>
</template>
