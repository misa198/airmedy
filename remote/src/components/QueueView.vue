<script setup lang="ts">
import Queue from './Queue.vue'

defineProps<{
  showQueue: boolean
}>()
</script>

<template>
  <div
    class="h-full lg:transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)] relative flex items-center justify-center"
    :class="!showQueue ? 'w-0' : 'w-full lg:w-1/2 lg:max-w-xl'">

    <Transition enter-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
      enter-from-class="opacity-0 translate-x-24" enter-to-class="opacity-100 translate-x-0"
      leave-active-class="transition-all duration-500 ease-[cubic-bezier(0.4,0,0.2,1)]"
      leave-from-class="opacity-100 translate-x-0" leave-to-class="opacity-0 translate-x-24">
      <Queue v-if="showQueue" class="h-full lg:h-[85%] w-full" />
    </Transition>
  </div>
</template>
