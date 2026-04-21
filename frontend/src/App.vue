<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>

<template>
  <main class="min-h-screen bg-background flex flex-col items-center justify-center p-8">
    <div class="p-8 rounded-lg bg-glass border border-border-glass backdrop-blur-md shadow-xl text-center">
      <h1 class="text-4xl font-bold bg-primary-gradient bg-clip-text text-transparent mb-4">
        Airmedy
      </h1>
      <p class="text-[#A1A1AA] mb-6">
        {{ t('common.home') }} - Modern Glassmorphic Music Player
      </p>
      <div class="flex gap-4 justify-center">
        <button class="px-6 py-2 rounded-full bg-primary hover:opacity-90 transition-opacity font-medium">
          {{ t('common.play') }}
        </button>
        <button class="px-6 py-2 rounded-full bg-glass-elevated border border-border-glass hover:bg-white/10 transition-colors">
          {{ t('common.shuffle') }}
        </button>
      </div>
    </div>
  </main>
</template>

<style>
/* App specific styles can go here */
</style>