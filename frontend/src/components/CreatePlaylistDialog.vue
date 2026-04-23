<script setup lang="ts">
import { ref, watch } from 'vue'
import { Input } from '@/components/ui/input'

const props = defineProps<{
  open: boolean
  initialName?: string
  title?: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  confirm: [name: string]
}>()

const name = ref(props.initialName ?? '')

watch(() => props.open, (val) => {
  if (val) name.value = props.initialName ?? ''
})

function submit() {
  if (!name.value.trim()) return
  emit('confirm', name.value.trim())
  emit('update:open', false)
}

function cancel() {
  emit('update:open', false)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center"
        @click.self="cancel"
      >
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="cancel" />
        <div
          class="relative z-10 w-80 rounded-xl bg-[#1A1A1A] ring-1 ring-white/[0.08] shadow-2xl p-5"
          @keydown.esc="cancel"
          @keydown.enter="submit"
        >
          <h3 class="text-sm font-semibold text-white mb-4">{{ title ?? 'New Playlist' }}</h3>
          <Input
            v-model="name"
            placeholder="Playlist name"
            class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/30 focus-visible:ring-white/20"
            autofocus
          />
          <div class="flex justify-end gap-2 mt-4">
            <button
              class="px-3 py-1.5 text-sm text-white/50 hover:text-white rounded-lg hover:bg-white/[0.05] transition-colors"
              @click="cancel"
            >Cancel</button>
            <button
              class="px-3 py-1.5 text-sm text-white bg-white/[0.12] hover:bg-white/[0.18] rounded-lg transition-colors font-medium disabled:opacity-40"
              :disabled="!name.trim()"
              @click="submit"
            >{{ title === 'Rename Playlist' ? 'Rename' : 'Create' }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
