<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Disc, Music, Search, User } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'
import AlbumCard from '@/components/AlbumCard.vue'
import { useSearchStore } from '@/stores/search'
import { usePlayerStore } from '@/stores/player'
import type { TrackDTO } from '../../bindings/changeme/internal/domain/models'

const router = useRouter()
const store = useSearchStore()
const playerStore = usePlayerStore()

const inputValue = ref(store.query)

watch(inputValue, (val) => {
  store.search(val)
})

function playTrack(track: TrackDTO) {
  if (!store.results) return
  const tracks = store.results.tracks?.filter(Boolean) as TrackDTO[]
  const idx = tracks.findIndex((t) => t.id === track.id)
  playerStore.playTracks(tracks, idx >= 0 ? idx : 0)
}

function navigateToAlbum(id: string) {
  router.push(`/albums/${id}`)
}

function navigateToArtist(id: string) {
  router.push(`/artists/${id}`)
}

function formatDuration(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

const hasTracks = () => (store.results?.tracks?.length ?? 0) > 0
const hasAlbums = () => (store.results?.albums?.length ?? 0) > 0
const hasArtists = () => (store.results?.artists?.length ?? 0) > 0
const hasResults = () => hasTracks() || hasAlbums() || hasArtists()
</script>

<template>
  <div class="flex flex-col h-full overflow-hidden">
    <!-- Search bar -->
    <div class="px-6 pt-6 pb-4 flex-shrink-0">
      <div class="relative max-w-xl">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-white/30 pointer-events-none" />
        <Input
          v-model="inputValue"
          placeholder="Search tracks, albums, artists..."
          class="pl-10 bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/30 focus-visible:ring-white/20"
          autofocus
        />
      </div>
    </div>

    <!-- Content area -->
    <div class="flex-1 overflow-y-auto px-6 pb-6 space-y-8">

      <!-- Empty state (no query) -->
      <div v-if="!inputValue.trim()" class="flex flex-col items-center justify-center h-64 text-center">
        <Search class="w-12 h-12 text-white/10 mb-4" />
        <p class="text-white/40 text-lg font-medium">Search your library</p>
        <p class="text-white/20 text-sm mt-1">Find tracks, albums, and artists</p>
      </div>

      <!-- Loading skeleton -->
      <div v-else-if="store.loading" class="space-y-8 mt-2">
        <div v-for="i in 2" :key="i" class="space-y-3">
          <div class="h-4 w-24 bg-white/[0.06] rounded animate-pulse" />
          <div class="flex gap-3">
            <div v-for="j in 4" :key="j" class="w-40 h-14 bg-white/[0.04] rounded-lg animate-pulse" />
          </div>
        </div>
      </div>

      <!-- No results -->
      <div
        v-else-if="inputValue.trim() && !store.loading && !hasResults()"
        class="flex flex-col items-center justify-center h-64 text-center"
      >
        <Music class="w-12 h-12 text-white/10 mb-4" />
        <p class="text-white/40 text-lg font-medium">No results found</p>
        <p class="text-white/20 text-sm mt-1">Try a different search term</p>
      </div>

      <!-- Results -->
      <template v-else-if="hasResults()">

        <!-- Tracks -->
        <section v-if="hasTracks()">
          <h2 class="text-sm font-semibold text-white/40 uppercase tracking-widest mb-3">Tracks</h2>
          <div class="space-y-0.5">
            <div
              v-for="track in store.results!.tracks!.filter(Boolean).slice(0, 8)"
              :key="track!.id"
              class="flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-white/[0.04] cursor-pointer group transition-colors"
              @dblclick="playTrack(track!)"
              @click.exact="playTrack(track!)"
            >
              <!-- Artwork -->
              <div class="w-9 h-9 flex-shrink-0 rounded bg-white/[0.06] overflow-hidden ring-1 ring-white/[0.06]">
                <img
                  v-if="track!.artwork_key"
                  :src="`/artwork/${track!.artwork_key}`"
                  class="w-full h-full object-cover"
                />
                <div v-else class="w-full h-full flex items-center justify-center text-white/20">
                  <Music class="w-4 h-4" />
                </div>
              </div>

              <!-- Info -->
              <div class="flex-1 min-w-0">
                <p class="text-sm font-medium text-white truncate">{{ track!.title }}</p>
                <p class="text-xs text-white/40 truncate">
                  {{ track!.artists?.map((a) => a?.name).join(', ') || track!.raw_artist_names }}
                </p>
              </div>

              <!-- Album -->
              <p class="text-xs text-white/30 truncate max-w-[160px] hidden md:block">
                {{ track!.album?.title }}
              </p>

              <!-- Duration -->
              <span class="text-xs text-white/30 flex-shrink-0">
                {{ formatDuration(track!.duration ?? 0) }}
              </span>
            </div>
          </div>
        </section>

        <!-- Albums -->
        <section v-if="hasAlbums()">
          <h2 class="text-sm font-semibold text-white/40 uppercase tracking-widest mb-3">Albums</h2>
          <div class="flex gap-4 overflow-x-auto pb-2 scrollbar-hide">
            <div
              v-for="album in store.results!.albums!.filter(Boolean)"
              :key="album!.id"
              class="flex-shrink-0 w-40"
            >
              <AlbumCard
                :album="album!"
                @click="navigateToAlbum(album!.id)"
                @play="(id) => { /* play album */ }"
                @artist-click="(id) => navigateToArtist(id)"
              />
            </div>
          </div>
        </section>

        <!-- Artists -->
        <section v-if="hasArtists()">
          <h2 class="text-sm font-semibold text-white/40 uppercase tracking-widest mb-3">Artists</h2>
          <div class="flex gap-3 overflow-x-auto pb-2 scrollbar-hide">
            <div
              v-for="artist in store.results!.artists!.filter(Boolean)"
              :key="artist!.id"
              class="flex-shrink-0 w-32 cursor-pointer group"
              @click="navigateToArtist(artist!.id)"
            >
              <div class="aspect-square rounded-full bg-white/[0.06] ring-1 ring-white/[0.06] flex items-center justify-center mb-2 overflow-hidden group-hover:bg-white/[0.1] transition-colors">
                <User class="w-8 h-8 text-white/20" />
              </div>
              <p class="text-xs text-center text-white/70 truncate group-hover:text-white transition-colors">{{ artist!.name }}</p>
            </div>
          </div>
        </section>

      </template>
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
