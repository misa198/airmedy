<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Folder, Settings,
  Play, Info, Blocks,
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import GeneralSettings from '@/components/settings/GeneralSettings.vue'
import LibrarySettings from '@/components/settings/LibrarySettings.vue'
import IntegrationsSettings from '@/components/settings/IntegrationsSettings.vue'
import PlaybackSettings from '@/components/settings/PlaybackSettings.vue'
import AboutSettings from '@/components/settings/AboutSettings.vue'

const { t } = useI18n()
const router = useRouter()

const props = defineProps<{
  category?: string
}>()

// State
const activeCategory = ref(props.category || 'general')

watch(() => props.category, (newCat) => {
  activeCategory.value = newCat || 'general'
})

const categories = computed(() => [
  { id: 'general', name: t('settings.categories.general'), icon: Settings },
  { id: 'library', name: t('settings.categories.library'), icon: Folder },
  { id: 'integrations', name: t('settings.categories.integrations', 'Integrations'), icon: Blocks },
  { id: 'playback', name: t('settings.categories.playback'), icon: Play },
  { id: 'about', name: t('settings.categories.about'), icon: Info },
])

const setCategory = (id: string) => {
  activeCategory.value = id
  router.replace(`/settings/${id}`)
}

</script>

<template>
  <div class="h-full flex flex-col md:flex-row bg-background text-foreground overflow-hidden">
    <!-- Sidebar -->
    <aside class="w-full md:w-56 border-r border-foreground/[0.06] bg-foreground/[0.02] flex-shrink-0 select-none">
      <div class="p-6">
        <h1 class="text-2xl font-bold mb-6 px-2">{{ t('settings.title') }}</h1>
        <nav class="space-y-1">
          <button
            v-for="cat in categories"
            :key="cat.id"
            @click="setCategory(cat.id)"
            :class="[
              'w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all group',
              activeCategory === cat.id 
                ? 'bg-primary text-primary-foreground shadow-sm shadow-primary/20' 
                : 'text-foreground opacity-80 hover:text-foreground hover:bg-foreground/[0.04]'
            ]"
          >
            <component :is="cat.icon" class="w-4 h-4" />
            {{ cat.name }}
          </button>
        </nav>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="flex-1 overflow-y-auto custom-scrollbar">
      <div class="max-w-3xl p-8 mx-auto">
        <!-- General Settings -->
        <GeneralSettings
          v-if="activeCategory === 'general'"
        />

        <!-- Library Settings -->
        <LibrarySettings
          v-if="activeCategory === 'library'"
        />

        <!-- Integrations -->
        <IntegrationsSettings
          v-if="activeCategory === 'integrations'"
        />

        <!-- Playback -->
        <PlaybackSettings v-if="activeCategory === 'playback'" />

        <!-- About -->
        <AboutSettings 
          v-if="activeCategory === 'about'" 
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
</style>
