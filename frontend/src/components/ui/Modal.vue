<script setup lang="ts">
defineProps<{
  open: boolean
  title: string
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center" @click.self="emit('close')">
        <div class="absolute inset-0 bg-background/60 backdrop-blur-sm" @click="emit('close')" />
        <div
          class="relative z-10 w-72 rounded-xl bg-[#1A1A1A] ring-1 ring-foreground/[0.08] shadow-2xl p-5"
          @keydown.esc="emit('close')">
          <h3 class="text-sm font-semibold text-foreground mb-4">{{ title }}</h3>
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.15s; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
</style>
