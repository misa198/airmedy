<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { cn } from '@airmedy/utils'

const props = defineProps<{
  text?: string
  containerClass?: string
  contentClass?: string
  pauseOnHover?: boolean
  fade?: boolean
}>()

const containerRef = ref<HTMLElement | null>(null)
const contentRef = ref<HTMLElement | null>(null)
const isOverflowing = ref(false)
const scrollDistance = ref(0)
const pauseDuration = 3
const duration = ref(pauseDuration)

const updateMarquee = async () => {
  await nextTick()
  if (!containerRef.value || !contentRef.value) return

  // Reset to measure correctly
  isOverflowing.value = false
  await nextTick()

  const containerWidth = containerRef.value.offsetWidth
  const contentWidth = contentRef.value.scrollWidth

  if (contentWidth > containerWidth + 1) { // +1 for subpixel rounding issues
    isOverflowing.value = true
    scrollDistance.value = contentWidth + 32
    duration.value = scrollDistance.value / 20 + pauseDuration
  } else {
    isOverflowing.value = false
    scrollDistance.value = 0
  }
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  updateMarquee()
  resizeObserver = new ResizeObserver(() => {
    updateMarquee()
  })
  if (containerRef.value) resizeObserver.observe(containerRef.value)
})

onUnmounted(() => {
  if (resizeObserver) resizeObserver.disconnect()
})

watch(() => [props.text, props.fade], () => {
  updateMarquee()
})
</script>

<template>
  <div
    ref="containerRef"
    :class="cn('overflow-hidden whitespace-nowrap min-w-0 w-full', props.fade && isOverflowing && 'marquee-fade', props.containerClass)"
    :title="text"
  >
    <div
      :class="cn(
        'relative w-fit min-w-full inline-block',
        isOverflowing && 'marquee-anim',
        props.pauseOnHover && 'hover:[animation-play-state:paused]',
        props.contentClass
      )"
      :style="{
        '--scroll-dist': `-${scrollDistance}px`,
        '--duration': `${duration}s`,
        '--pause-end': `${pauseDuration / duration * 100}%`,
      }"
    >
      <span ref="contentRef" class="inline-block"><slot>{{ text }}</slot></span>
      <span v-if="isOverflowing" aria-hidden="true" inert class="absolute left-full top-0 pl-8"><slot>{{ text }}</slot></span>
    </div>
  </div>
</template>

<style scoped>
.marquee-fade {
  mask-image: linear-gradient(to right, currentColor calc(100% - 16px), transparent);
}

.marquee-anim {
  animation: marquee-scroll var(--duration) linear(0, 0 var(--pause-end), 1) infinite;
}

@keyframes marquee-scroll {
  from { transform: translateX(0); }
  to { transform: translateX(var(--scroll-dist)); }
}

@media (prefers-reduced-motion: reduce) {
  .marquee-anim {
    animation: none;
  }
}
</style>
