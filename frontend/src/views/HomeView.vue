<script setup lang="ts">
import { computed, ref } from 'vue'
import { BarChart3, House } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import HomeOverview from '@/components/home/HomeOverview.vue'
import HomeAnalysis from '@/components/home/HomeAnalysis.vue'
import HomeHeader from '@/components/home/HomeHeader.vue'

const { t } = useI18n()
const activeTab = ref('overview')
const tabs = computed(() => [
  { value: 'overview', label: t('sidebar.home'), icon: House },
  { value: 'analysis', label: t('analytics.title'), icon: BarChart3 },
])
const overviewTitle = computed(() => {
  const hour = new Date().getHours()
  return hour < 12 ? t('home.greeting.morning') : hour < 17 ? t('home.greeting.afternoon') : hour < 21 ? t('home.greeting.evening') : t('home.greeting.night')
})
const overviewSubtitle = computed(() => [t('home.greeting.welcome'), t('home.greeting.ready'), t('home.greeting.discover')][new Date().getHours() % 3])
const header = computed(() => activeTab.value === 'overview'
  ? { title: overviewTitle.value, subtitle: overviewSubtitle.value }
  : { title: t('analytics.title'), subtitle: t('analytics.subtitle') })
</script>

<template>
  <div class="h-full overflow-y-scroll p-8 [scrollbar-gutter:stable] custom-scrollbar select-none">
    <HomeHeader :title="header.title" :subtitle="header.subtitle" :model-value="activeTab" :tabs="tabs" @update:model-value="activeTab = $event" />
    <div v-if="activeTab === 'overview'" class="mt-16">
      <HomeOverview />
    </div>
    <div v-else class="mt-8">
      <HomeAnalysis />
    </div>
  </div>
</template>
