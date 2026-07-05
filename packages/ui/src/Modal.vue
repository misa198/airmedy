<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  open: boolean
  title?: string
  widthClass?: string
  bare?: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

// Only close when the press STARTED on the backdrop. Prevents closing when a
// text selection drag inside an input ends on the backdrop (mouseup outside).
const pressedOnBackdrop = ref(false)

function onPointerDown(e: PointerEvent) {
  pressedOnBackdrop.value = e.target === e.currentTarget
}

function onBackdropPointerDown() {
  pressedOnBackdrop.value = true
}

function onClickClose() {
  if (pressedOnBackdrop.value) emit('close')
  pressedOnBackdrop.value = false
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="open" class="fixed inset-0 z-50 flex items-center justify-center transform-gpu will-change-[opacity]" @pointerdown.self="onPointerDown" @click.self="onClickClose">
        <div class="backdrop absolute inset-0 bg-background/60 backdrop-blur-sm transform-gpu" @pointerdown="onBackdropPointerDown" @click="onClickClose" />
        <div
          class="modal-content relative z-10 rounded-3xl bg-glass-modal backdrop-blur-3xl ring-1 ring-border-glass shadow-2xl transform-gpu isolate max-h-[85vh] overflow-hidden"
          :class="widthClass || 'w-72'"
          @keydown.esc="emit('close')">
          <slot v-if="bare" />
          <div v-else class="flex flex-col max-h-[85vh]">
            <div class="min-h-0 flex-1 overflow-y-auto p-5">
              <h3 v-if="title" class="text-base font-semibold text-foreground mb-4">{{ title }}</h3>
              <slot />
            </div>
            <div v-if="$slots.footer" class="shrink-0 px-5 pb-5 border-t border-foreground/10 pt-4">
              <slot name="footer" />
            </div>
          </div>
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
