<script setup lang="ts">
defineProps<{
  lines: string[]
}>()
</script>

<template>
  <div class="h-full overflow-y-auto px-8 py-16 scrollbar-hide">
    <div class="max-w-2xl mx-auto space-y-4">
      <p
        v-for="(line, index) in lines"
        :key="index"
        class="text-lg md:text-xl font-medium text-white/70 leading-relaxed select-text"
      >
        {{ line }}
      </p>
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
