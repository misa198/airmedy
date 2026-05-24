<script setup lang="ts">
defineProps<{
  open: boolean
  title?: string
  widthClass?: string
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center transform-gpu will-change-[opacity]" @click.self="emit('close')">
        <div class="backdrop absolute inset-0 bg-background/60 backdrop-blur-sm transform-gpu" @click="emit('close')" />
        <div
          class="modal-content relative z-10 rounded-3xl bg-glass-elevated backdrop-blur-xl ring-1 ring-border-glass shadow-2xl p-5 transform-gpu isolate"
          :class="widthClass || 'w-72'"
          @keydown.esc="emit('close')">
          <h3 v-if="title" class="text-base font-semibold text-foreground mb-4">{{ title }}</h3>
          <slot />
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* Parent transition (handles backdrop) */
.modal-fade-enter-active, .modal-fade-leave-active { 
  transition: opacity 0.2s ease-out; 
}
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }

/* Content transition (delayed slightly to allow backdrop to show first) */
.modal-fade-enter-active .modal-content { 
  transition: transform 0.3s ease-out, opacity 0.3s ease-out;
  transition-delay: 0.05s;
}
.modal-fade-enter-from .modal-content { 
  transform: scale(0.95);
  opacity: 0;
}

/* Leave transition (no delay for immediate feel) */
.modal-fade-leave-active .modal-content {
  transition: transform 0.2s ease-in, opacity 0.2s ease-in;
}
.modal-fade-leave-to .modal-content {
  transform: scale(0.98);
  opacity: 0;
}
</style>
