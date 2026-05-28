<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Lock } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { send } from '../ws'
import { usePlayerStore } from '../stores/player'

const { t } = useI18n()
const store = usePlayerStore()
const digits = ref<string[]>(['', '', '', ''])
const inputs = ref<HTMLInputElement[]>([])
const error = computed(() => store.authState === 'failed')
const keyboardOffset = ref(0)

function onViewportResize() {
  const vv = window.visualViewport
  if (!vv) return
  const hidden = window.innerHeight - vv.height - vv.offsetTop
  keyboardOffset.value = hidden > 0 ? hidden : 0
}

onMounted(() => {
  window.visualViewport?.addEventListener('resize', onViewportResize)
  window.visualViewport?.addEventListener('scroll', onViewportResize)
})

onUnmounted(() => {
  window.visualViewport?.removeEventListener('resize', onViewportResize)
  window.visualViewport?.removeEventListener('scroll', onViewportResize)
})

function onInput(index: number, e: Event) {
  const val = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(-1)
  digits.value[index] = val
  if (val && index < 3) {
    inputs.value[index + 1]?.focus()
  }
  if (digits.value.every(d => d !== '')) {
    submit()
  }
}

function onKeydown(index: number, e: KeyboardEvent) {
  if (e.key === 'Backspace' && !digits.value[index] && index > 0) {
    digits.value[index - 1] = ''
    inputs.value[index - 1]?.focus()
  }
}

function submit() {
  const pin = digits.value.join('')
  if (pin.length === 4) {
    store.setAuthState('idle')
    send({ type: 'auth', password: pin })
  }
}

function reset() {
  digits.value = ['', '', '', '']
  inputs.value[0]?.focus()
}
</script>

<template>
  <div
    class="flex flex-col items-center justify-center min-h-dvh gap-10 px-6 bg-background transition-[padding] duration-200"
    :style="{ paddingBottom: keyboardOffset + 'px' }"
  >
    <div class="flex flex-col items-center gap-4">
      <div class="w-20 h-20 rounded-2xl overflow-hidden mb-2 shadow-xl ring-1 ring-white/10">
        <img src="/airmedy-md.png" class="w-full h-full object-cover" />
      </div>
      <div class="text-center">
        <h1 class="text-3xl font-black text-foreground tracking-tight">Airmedy</h1>
        <p class="text-sm font-medium text-muted-foreground/60 mt-1.5 flex items-center justify-center gap-1.5">
          <Lock class="w-3.5 h-3.5" />
          {{ t('auth.enter_pin') }}
        </p>
      </div>
    </div>

    <div class="flex gap-3">
      <input
        v-for="(_, i) in digits"
        :key="i"
        :ref="(el) => { if (el) inputs[i] = el as HTMLInputElement }"
        :value="digits[i]"
        type="text"
        inputmode="numeric"
        maxlength="1"
        class="w-14 h-18 text-center text-3xl font-bold rounded-xl bg-white/[0.03] border-2 transition-all outline-none"
        :class="error ? 'border-red-500/50 text-red-400 bg-red-500/5' : digits[i] ? 'border-primary text-foreground bg-primary/5' : 'border-white/5 text-foreground focus:border-white/20'"
        @input="onInput(i, $event)"
        @keydown="onKeydown(i, $event)"
        @focus="(e: FocusEvent) => (e.target as HTMLInputElement).select()"
      />
    </div>

    <div class="h-6 flex flex-col items-center gap-4">
      <p v-if="error" class="text-sm font-semibold text-red-400">{{ t('auth.incorrect_pin') }}</p>
      <button
        v-if="error"
        @click="reset"
        class="text-xs font-bold text-muted-foreground/40 uppercase tracking-widest hover:text-muted-foreground transition-colors"
      >
        {{ t('auth.clear_all') }}
      </button>
    </div>
  </div>
</template>
