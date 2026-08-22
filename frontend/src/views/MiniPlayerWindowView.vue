<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { Events } from '@wailsio/runtime'
import { usePlayerStore } from '@/stores/player'
import { useDeviceStore } from '@/stores/device'
import MiniPlayerFloating from '@/components/MiniPlayerFloating.vue'

const playerStore = usePlayerStore()
const deviceStore = useDeviceStore()

let offWindowShow: (() => void) | null = null
let previousDocumentBackground = ''
let previousBodyBackground = ''

onMounted(() => {
	previousDocumentBackground = document.documentElement.style.backgroundColor
	previousBodyBackground = document.body.style.backgroundColor
	document.documentElement.style.backgroundColor = 'var(--bg-main)'
	document.body.style.backgroundColor = 'var(--bg-main)'
	playerStore.init()
  deviceStore.init()
  offWindowShow = Events.On(Events.Types.Common.WindowShow, () => {
    playerStore.syncState()
  })
})

onUnmounted(() => {
	offWindowShow?.()
	deviceStore.dispose()
	document.documentElement.style.backgroundColor = previousDocumentBackground
	document.body.style.backgroundColor = previousBodyBackground
})
</script>

<template>
  <div class="h-full w-full bg-[color:var(--bg-main)] text-foreground overflow-hidden">
    <MiniPlayerFloating />
  </div>
</template>
