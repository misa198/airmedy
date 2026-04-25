import { ref, onActivated } from 'vue'

export function useRestoreScroll() {
  const scrollContainerRef = ref<HTMLElement | null>(null)
  const lastScrollTop = ref(0)

  const handleScroll = (event: Event) => {
    const target = event.target as HTMLElement
    if (target) {
      lastScrollTop.value = target.scrollTop
    }
  }

  onActivated(() => {
    if (scrollContainerRef.value && lastScrollTop.value > 0) {
      setTimeout(() => {
        if (scrollContainerRef.value) {
          scrollContainerRef.value.scrollTop = lastScrollTop.value
        }
      }, 0)
    }
  })

  return {
    scrollContainerRef,
    lastScrollTop,
    handleScroll
  }
}
