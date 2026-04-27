<script setup lang="ts">
import { ref, computed } from 'vue'
import {
  Folder, Settings,
  Sliders, Info
} from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import EQPanel from '@/components/EQPanel.vue'
import GeneralSettings from '@/components/settings/GeneralSettings.vue'
import LibrarySettings from '@/components/settings/LibrarySettings.vue'
import AboutSettings from '@/components/settings/AboutSettings.vue'

const { t } = useI18n()

// State
const activeCategory = ref('general')

const categories = computed(() => [
  { id: 'general', name: t('settings.categories.general'), icon: Settings },
  { id: 'library', name: t('settings.categories.library'), icon: Folder },
  { id: 'equalization', name: t('settings.categories.equalization'), icon: Sliders },
  { id: 'about', name: t('settings.categories.about'), icon: Info },
])

</script>

<template>
  <div class="h-full flex flex-col md:flex-row bg-background text-foreground overflow-hidden">
    <!-- Sidebar -->
    <aside class="w-full md:w-64 border-r border-foreground/[0.06] bg-foreground/[0.02] flex-shrink-0 select-none">
      <div class="p-6">
        <h1 class="text-2xl font-bold mb-6 px-2">{{ t('settings.title') }}</h1>
        <nav class="space-y-1">
          <button
            v-for="cat in categories"
            :key="cat.id"
            @click="activeCategory = cat.id"
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

        <!-- Equalization -->
        <div v-if="activeCategory === 'equalization'" class="space-y-6 animate-in fade-in slide-in-from-bottom-2 duration-500">
          <h2 class="text-xl font-bold mb-4 select-none">{{ t('settings.equalizer.title') }}</h2>
          <div class="bg-card rounded-2xl border border-foreground/[0.06] p-6">
            <EQPanel />
          </div>
        </div>

        <!-- About -->
        <AboutSettings 
          v-if="activeCategory === 'about'" 
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 10px;
}
.dark .custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
}
</style>
