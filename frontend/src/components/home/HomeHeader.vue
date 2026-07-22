<script setup lang="ts">
import type { Component } from 'vue'
import { TabSwitcher } from '@airmedy/ui'

const props = defineProps<{
  title: string
  subtitle: string
  modelValue: string
  tabs: { value: string; label: string; icon: Component }[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()
</script>

<template>
  <header class="flex items-start justify-between gap-4">
    <div>
      <h1 class="mb-2 text-4xl font-bold tracking-tight">{{ props.title }}</h1>
      <p class="text-xl text-[color:var(--text-muted)]">{{ props.subtitle }}</p>
    </div>
    <TabSwitcher :model-value="props.modelValue" :options="props.tabs" mandatory @update:model-value="value => value && emit('update:modelValue', value)" />
  </header>
</template>
