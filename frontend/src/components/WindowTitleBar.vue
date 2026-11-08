<script setup lang="ts">
import { useDeviceStore } from '@/stores/device'
import { ChevronLeft, ChevronRight, Copy, Folder, Minus, Settings, Smartphone, Square, X } from '@lucide/vue'
import { Window } from '@wailsio/runtime'
import { onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const deviceStore = useDeviceStore()
const router = useRouter()
const { t } = useI18n()
const canGoBack = ref(false)
const canGoForward = ref(false)

function updateNavigation() {
  canGoBack.value = typeof window.history.state?.back === 'string'
  canGoForward.value = typeof window.history.state?.forward === 'string'
}

updateNavigation()
const removeAfterEach = router.afterEach(updateNavigation)
onUnmounted(removeAfterEach)

const windowControlClass = 'flex h-full w-10 items-center justify-center text-foreground focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-foreground transition-all duration-300 ease-[cubic-bezier(0.4,0,0.2,1)] outline-none'
const shortcuts = [
  { path: '/settings/mobile-devices', label: 'settings.categories.mobile_devices', icon: Smartphone },
  { path: '/settings/library', label: 'settings.categories.library', icon: Folder },
  { path: '/settings', label: 'settings.title', icon: Settings },
]

async function minimize() {
  try {
    await Window.Minimise()
  } catch (error) {
    console.error('Failed to minimize window', error)
  }
}

async function close() {
  try {
    await Window.Close()
  } catch (error) {
    console.error('Failed to close window', error)
  }
}
</script>

<template>
  <div
    class="relative z-[1002] flex h-8 w-full shrink-0 select-none justify-end bg-background"
    style="--wails-draggable: drag"
    @dblclick.stop="deviceStore.toggleMaximize"
  >
    <span
      class="ml-5 size-5 shrink-0 self-center bg-foreground"
      style="-webkit-mask: url(/airmedy.svg) center / contain no-repeat; mask: url(/airmedy.svg) center / contain no-repeat"
      aria-hidden="true"
    />
    <div class="ml-2 mr-auto flex h-full" style="--wails-draggable: no-drag" @dblclick.stop>
      <button
        :class="windowControlClass"
        class="disabled:cursor-default disabled:opacity-30 w-8!"
        type="button"
        :aria-label="t('common.back')"
        :title="t('common.back')"
        :disabled="!canGoBack"
        @click="router.back()"
      >
        <ChevronLeft class="size-5.5" :stroke-width="1.5" aria-hidden="true" />
      </button>
      <button
        :class="windowControlClass"
        class="disabled:cursor-default disabled:opacity-30 w-8!"
        type="button"
        :aria-label="t('common.forward')"
        :title="t('common.forward')"
        :disabled="!canGoForward"
        @click="router.forward()"
      >
        <ChevronRight class="size-5.5" :stroke-width="1.5" aria-hidden="true" />
      </button>
    </div>
    <div class="mr-3 flex h-full" style="--wails-draggable: no-drag" @dblclick.stop>
      <button
        v-for="shortcut in shortcuts"
        :key="shortcut.path"
        :class="windowControlClass"
        type="button"
        :aria-label="t(shortcut.label)"
        :title="t(shortcut.label)"
        @click="router.push(shortcut.path)"
      >
        <component :is="shortcut.icon" class="size-3.5" :stroke-width="1.5" aria-hidden="true" />
      </button>
    </div>
    <div class="flex h-full" style="--wails-draggable: no-drag" @dblclick.stop>
      <button
        :class="windowControlClass"
        type="button"
        :aria-label="t('common.minimize')"
        class="cursor-default hover:bg-foreground/[0.04]"
        :title="t('common.minimize')"
        @click="minimize"
      >
        <Minus class="size-3.5" :stroke-width="1.5" aria-hidden="true" />
      </button>
      <button
        :class="windowControlClass"
        type="button"
        :aria-label="t(deviceStore.isWindowMaximized ? 'common.restore' : 'common.maximize')"
        class="cursor-default hover:bg-foreground/[0.04]"
        :title="t(deviceStore.isWindowMaximized ? 'common.restore' : 'common.maximize')"
        @click="deviceStore.toggleMaximize"
      >
        <Copy v-if="deviceStore.isWindowMaximized" class="size-3" :stroke-width="1.5" aria-hidden="true" />
        <Square v-else class="size-3" :stroke-width="1.5" aria-hidden="true" />
      </button>
      <button
        :class="windowControlClass"
        type="button"
        :aria-label="t('common.close')"
        class="cursor-default hover:bg-window-close hover:text-window-close-foreground"
        :title="t('common.close')"
        @click="close"
      >
        <X class="size-4" :stroke-width="1.5" aria-hidden="true" />
      </button>
    </div>
  </div>
</template>
