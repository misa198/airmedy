<script setup lang="ts">
import { computed, ref, watch } from 'vue'

interface LyricLine {
  text: string
  time: number
}

const props = defineProps<{
  lines: LyricLine[]
  currentPosition: number
}>()

const emit = defineEmits<{
  seek: [time: number]
}>()

const activeIndex = computed(() => {
  const idx = [...props.lines].reverse().findIndex(
    line => line.time <= props.currentPosition
  )
  return idx !== -1 ? props.lines.length - 1 - idx : -1
})

const scrollContainer = ref<HTMLElement | null>(null)
const lineRefs = ref<HTMLElement[]>([])

watch(activeIndex, (newIndex) => {
  if (newIndex === -1 || !lineRefs.value[newIndex] || !scrollContainer.value) return
  const container = scrollContainer.value
  const el = lineRefs.value[newIndex]
  container.scrollTo({
    top: el.offsetTop - container.clientHeight / 2 + el.clientHeight / 2,
    behavior: 'smooth',
  })
})
</script>

<template>
  <div ref="scrollContainer" class="h-full overflow-y-auto px-8 py-48 scrollbar-hide scroll-smooth">
    <div class="max-w-2xl mx-auto space-y-10">
      <div
        v-for="(line, index) in lines"
        :key="index"
        ref="lineRefs"
        class="text-2xl md:text-4xl font-extrabold transition-all duration-100 cursor-pointer select-none origin-left py-2"
        :class="[
          index === activeIndex
            ? 'text-white scale-105 blur-none opacity-100'
            : index < activeIndex
              ? 'text-white/20 blur-[0.5px] opacity-60 hover:text-white/40'
              : 'text-white/30 blur-[1px] opacity-40 hover:text-white/60 hover:blur-none',
        ]"
        @click="emit('seek', line.time)"
      >
        {{ line.text }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
