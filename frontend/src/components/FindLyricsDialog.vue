<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, X, Loader2, Music, User, Globe } from 'lucide-vue-next'
import Modal from '@/components/ui/Modal.vue'
import Input from '@/components/ui/input/Input.vue'
import { useFindLyricsDialog } from '@/composables/useFindLyricsDialog'
import { usePlayerStore } from '@/stores/player'
import * as LyricsService from '../../bindings/airmedy/internal/infra/wails/lyricsservice'
import type { LyricsSearchResult } from '../../bindings/airmedy/internal/domain/models'

const { t } = useI18n()
const { isVisible, targetTrack, close } = useFindLyricsDialog()
const playerStore = usePlayerStore()

const searchTitle = ref('')
const searchArtist = ref('')
const isSearching = ref(false)
const results = ref<LyricsSearchResult[]>([])
const selectedIndex = ref(-1)

watch(isVisible, (val) => {
  if (val && targetTrack.value) {
    searchTitle.value = targetTrack.value.title
    searchArtist.value = targetTrack.value.artists?.[0]?.name || ''
    results.value = []
    selectedIndex.value = -1
  }
})

async function search() {
  if (!searchTitle.value || isSearching.value) return
  isSearching.value = true
  results.value = []
  selectedIndex.value = -1
  try {
    const res = await LyricsService.SearchLyrics(searchTitle.value, searchArtist.value, targetTrack.value?.duration || 0)
    results.value = (res || []).filter((r): r is LyricsSearchResult => !!r)
  } catch (e) {
    console.error('Failed to search lyrics', e)
  } finally {
    isSearching.value = false
  }
}

async function save() {
  if (selectedIndex.value === -1 || !targetTrack.value) return
  const selected = results.value[selectedIndex.value]
  try {
    await LyricsService.SaveLyrics(targetTrack.value.id, selected.content, selected.source)
    
    // Update player store if it's the current track
    if (playerStore.currentTrack?.id === targetTrack.value.id) {
      playerStore.lyrics = {
        track_id: targetTrack.value.id,
        content: selected.content,
        source: selected.source,
        meta_content: playerStore.lyrics?.meta_content || '',
        meta_source: playerStore.lyrics?.meta_source || '',
        created_at: '',
        updated_at: ''
      } as any
    }
    
    close()
  } catch (e) {
    console.error('Failed to save lyrics', e)
  }
}
</script>

<template>
  <Modal
    :open="isVisible"
    width-class="max-w-4xl w-full h-[80vh] !p-0 flex flex-col overflow-hidden"
    @close="close"
  >
    <!-- Header -->
    <div class="flex items-center justify-between p-4 border-b border-border/50">
      <h2 class="text-xl font-bold flex items-center gap-2">
        <Search class="w-5 h-5" />
        {{ t('find_lyrics.title') }}
      </h2>
      <button class="p-2 hover:bg-hover rounded-full transition-colors" @click="close">
        <X class="w-5 h-5" />
      </button>
    </div>

    <div class="flex-1 flex overflow-hidden">
      <!-- Left: Search & List -->
      <div class="w-1/2 flex flex-col border-r border-border/50">
        <div class="p-4 space-y-4 border-b border-border/50">
          <div class="space-y-2">
            <label class="text-xs font-medium text-muted-foreground uppercase">{{ t('find_lyrics.track_title') }}</label>
            <div class="relative">
              <Music class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground z-10 pointer-events-none" />
              <Input 
                v-model="searchTitle" 
                type="text" 
                class="pl-10" 
                clearable
                @keyup.enter="search" 
              />
            </div>
          </div>
          <div class="space-y-2">
            <label class="text-xs font-medium text-muted-foreground uppercase">{{ t('find_lyrics.track_artist') }}</label>
            <div class="relative">
              <User class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground z-10 pointer-events-none" />
              <Input 
                v-model="searchArtist" 
                type="text" 
                class="pl-10" 
                clearable
                @keyup.enter="search" 
              />
            </div>
          </div>
          <button class="w-full bg-primary hover:bg-primary/90 text-primary-foreground font-bold py-2 rounded-lg flex items-center justify-center gap-2 transition-colors disabled:opacity-50" :disabled="isSearching" @click="search">
            <Loader2 v-if="isSearching" class="w-4 h-4 animate-spin" />
            <Search v-else class="w-4 h-4" />
            {{ t('find_lyrics.search') }}
          </button>
        </div>

        <div class="flex-1 overflow-y-auto p-2 space-y-1">
          <div v-if="results.length === 0 && !isSearching" class="h-full flex flex-col items-center justify-center text-muted-foreground p-4 text-center">
            <Search class="w-12 h-12 mb-2 opacity-20" />
            <p>{{ t('find_lyrics.no_results') }}</p>
          </div>

          <div v-for="(res, index) in results" :key="index" class="p-3 rounded-lg cursor-pointer transition-colors flex items-center gap-3" :class="selectedIndex === index ? 'bg-primary text-primary-foreground' : 'hover:bg-hover'" @click="selectedIndex = index">
            <div class="flex-1 min-w-0">
              <div class="font-medium truncate">{{ res.track_name }}</div>
              <div class="text-xs truncate opacity-70">{{ res.artist_name }}</div>
            </div>
            <div class="flex flex-col items-end gap-1">
              <div class="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded border border-current opacity-60">
                {{ res.provider }}
              </div>
              <div class="text-[10px] opacity-60">
                {{ Math.floor(res.duration / 60) }}:{{ (res.duration % 60).toString().padStart(2, '0') }}
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Preview -->
      <div class="w-1/2 flex flex-col bg-input/20">
        <div class="p-4 border-b border-border/50">
          <h3 class="text-xs font-medium text-muted-foreground uppercase flex items-center gap-2">
            <Globe class="w-3 h-3" />
            {{ t('find_lyrics.lyrics_preview') }}
          </h3>
        </div>
        <div class="flex-1 overflow-y-auto p-6 font-medium leading-relaxed">
          <template v-if="selectedIndex !== -1">
            <pre class="whitespace-pre-wrap font-sans text-sm">{{ results[selectedIndex].content }}</pre>
          </template>
          <div v-else class="h-full flex flex-col items-center justify-center text-muted-foreground">
            <Music class="w-12 h-12 mb-2 opacity-20" />
            <p>{{ t('find_lyrics.select_to_preview') }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Footer -->
    <div class="p-4 border-t border-border/50 flex justify-end gap-3 bg-card mt-auto">
      <button class="px-6 py-2 hover:bg-hover rounded-lg font-medium transition-colors" @click="close">
        {{ t('common.cancel') }}
      </button>
      <button class="px-6 py-2 bg-primary hover:bg-primary/90 text-primary-foreground rounded-lg font-bold transition-colors disabled:opacity-50" :disabled="selectedIndex === -1" @click="save">
        {{ t('find_lyrics.save') }}
      </button>
    </div>
  </Modal>
</template>

<style scoped></style>
