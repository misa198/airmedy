<script setup lang="ts">
import { ref, watch, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { Disc, Music, Search, User, Library, PenTool } from '@lucide/vue'
import LazyImg from '@/components/LazyImg.vue'
import { Input } from '@airmedy/ui'
import AlbumCard from '@/components/AlbumCard.vue'
import ArtistCard from '@/components/ArtistCard.vue'
import PlaylistCard from '@/components/PlaylistCard.vue'
import ComposerCard from '@/components/ComposerCard.vue'
import SearchSection from '@/components/SearchSection.vue'
import TrackContextMenu from '@/components/TrackContextMenu.vue'
import ContextMenu from '@/components/ContextMenu.vue'
import { useSearchStore } from '@/stores/search'
import { usePlayerStore } from '@/stores/player'
import { useContextMenu } from '@/composables/useContextMenu'
import { useAlbumContextMenu } from '@/composables/useAlbumContextMenu'
import { useLibrarySync } from '@/composables/useLibrarySync'
import type { AlbumDTO, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { buildArtworkUrl } from '@airmedy/utils'

const router = useRouter()
const store = useSearchStore()
const playerStore = usePlayerStore()

const inputValue = ref(store.query)
const searchInput = ref<InstanceType<typeof Input>>()
const trackContextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)
const albumContextMenu = useContextMenu()
const { buildMenuItems: buildAlbumMenuItems } = useAlbumContextMenu()

watch(inputValue, (val) => {
  store.search(val)
})

// Search results are a stored response, not a live query. Re-run the current
// query after metadata/import mutations; useLibrarySync defers this while the
// route is cached by KeepAlive and refreshes when it becomes visible again.
useLibrarySync(() => {
  void store.refresh()
})

onMounted(() => {
  searchInput.value?.focus()
})

onActivated(() => {
  searchInput.value?.focus()
})

function navigateToAlbum(id?: string) {
  if (!id) return
  router.push(`/albums/${id}`)
}

function playSearchTrack(track: TrackDTO) {
  const list = (store.results?.tracks?.filter(Boolean) ?? []) as TrackDTO[]
  const idx = list.findIndex(t => t.id === track.id)
  playerStore.playTracks(list, idx === -1 ? 0 : idx)
}

function openTrackContextMenu(event: MouseEvent, track: TrackDTO) {
  trackContextMenu.value?.open(event, track)
}

function openAlbumContextMenu(event: MouseEvent, album: AlbumDTO) {
  albumContextMenu.open(event, buildAlbumMenuItems(album))
}

function navigateToArtist(id: string) {
  router.push(`/artists/${id}`)
}

function navigateToPlaylist(id: string) {
  router.push(`/playlists/${id}`)
}

function navigateToComposer(id: string) {
  router.push(`/composers/${id}`)
}

const hasTracks = () => (store.results?.tracks?.length ?? 0) > 0
const hasAlbums = () => (store.results?.albums?.length ?? 0) > 0
const hasArtists = () => (store.results?.artists?.length ?? 0) > 0
const hasPlaylists = () => (store.results?.playlists?.length ?? 0) > 0
const hasComposers = () => (store.results?.composers?.length ?? 0) > 0
const hasResults = () => hasTracks() || hasAlbums() || hasArtists() || hasPlaylists() || hasComposers()
</script>

<template>
  <div class="flex flex-col h-full overflow-hidden">
    <!-- Search bar -->
    <div class="px-8 pt-8 pb-4 flex-shrink-0">
      <div class="relative max-w-xl">
        <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-subdued pointer-events-none" />
        <Input ref="searchInput" v-model="inputValue" :placeholder="$t('library.search_placeholder')" class="pl-10 pr-4" clearable />
      </div>
    </div>

    <!-- Content area -->
    <div class="flex-1 overflow-y-auto px-8 pb-8 space-y-12 custom-scrollbar select-none">

      <!-- Empty state (no query) -->
      <div v-if="!inputValue.trim()" class="flex flex-col items-center justify-center h-64 text-center">
        <div
          class="w-20 h-20 bg-foreground/[0.03] rounded-3xl flex items-center justify-center mb-6 ring-1 ring-foreground/[0.05]">
          <Search class="w-10 h-10 text-foreground opacity-30" />
        </div>
        <p class="text-dim text-xl font-semibold">{{ $t('library.search_placeholder') }}</p>
        <p class="text-dimmer mt-2 max-w-xs">{{ $t('library.search_description') }}</p>
      </div>

      <!-- Loading skeleton -->
      <div v-else-if="store.loading" class="space-y-12 mt-4">
        <div v-for="i in 2" :key="i" class="space-y-4">
          <div class="h-8 w-48 bg-foreground/[0.06] rounded-lg animate-pulse" />
          <div class="flex gap-6">
            <div v-for="j in 4" :key="j" class="w-48 aspect-square bg-foreground/[0.04] rounded-xl animate-pulse" />
          </div>
        </div>
      </div>

      <!-- No results -->
      <div v-else-if="inputValue.trim() && !store.loading && !hasResults()"
        class="flex flex-col items-center justify-center h-64 text-center">
        <div
          class="w-20 h-20 bg-foreground/[0.03] rounded-3xl flex items-center justify-center mb-6 ring-1 ring-foreground/[0.05]">
          <Music class="w-10 h-10 text-foreground opacity-30" />
        </div>
        <p class="text-dim text-xl font-semibold">{{ $t('library.no_results') }}</p>
        <p class="text-dimmer mt-2">{{ $t('library.try_different_search') }}</p>
      </div>

      <!-- Results -->
      <div v-else-if="hasResults()" class="space-y-16 py-4">

        <!-- Tracks (3 rows) -->
        <SearchSection v-if="hasTracks()" :title="$t('library.tracks')" :icon="Music"
          :items="store.results!.tracks!.filter(Boolean)" id="search-tracks" :rows="3">
          <template #default="{ item: track }">
            <div
              class="flex items-center gap-3 p-2 rounded-xl hover:bg-foreground/[0.04] cursor-pointer group transition-all"
              @click="playSearchTrack(track)"
              @contextmenu.prevent="openTrackContextMenu($event, track)">
              <div
                class="w-12 h-12 flex-shrink-0 rounded-lg bg-foreground/[0.06] overflow-hidden ring-1 ring-foreground/[0.06]">
                <LazyImg v-if="track.artwork_key || track.album?.artwork_key"
                  :src="buildArtworkUrl(track.artwork_key || track.album?.artwork_key, 'sm')"
                  class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" />
                <div v-else class="w-full h-full flex items-center justify-center text-dimmer">
                  <Music class="w-5 h-5" />
                </div>
              </div>
              <div class="min-w-0">
                <p class="text-sm font-semibold text-foreground truncate group-hover:text-primary transition-colors">{{
                  track.title }}</p>
                <p class="text-xs text-dim truncate">
                  {{track.artists?.map((a) => a?.name).join(', ') || track.raw_artist_names}}
                </p>
              </div>
            </div>
          </template>
        </SearchSection>

        <!-- Albums -->
        <SearchSection v-if="hasAlbums()" :title="$t('library.albums')" :icon="Disc"
          :items="store.results!.albums!.filter(Boolean)" id="search-albums">
          <template #default="{ item: album }">
            <AlbumCard :album="album" :show-play="false" @click="navigateToAlbum(album.id)" @artist-click="navigateToArtist"
              @contextmenu="openAlbumContextMenu" />
          </template>
        </SearchSection>

        <!-- Artists -->
        <SearchSection v-if="hasArtists()" :title="$t('library.artists')" :icon="User"
          :items="store.results!.artists!.filter(Boolean)" id="search-artists">
          <template #default="{ item: artist }">
            <ArtistCard :artist="artist" @click="navigateToArtist(artist.id)" />
          </template>
        </SearchSection>

        <!-- Playlists -->
        <SearchSection v-if="hasPlaylists()" :title="$t('library.playlists')" :icon="Library"
          :items="store.results!.playlists!.filter(Boolean)" id="search-playlists">
          <template #default="{ item: playlist }">
            <PlaylistCard :playlist="playlist" :tracks="(store.results!.playlist_tracks?.[playlist.id]?.filter(Boolean) as TrackDTO[])" @click="navigateToPlaylist(playlist.id)" />
          </template>
        </SearchSection>

        <!-- Composers -->
        <SearchSection v-if="hasComposers()" :title="$t('library.composers')" :icon="PenTool"
          :items="store.results!.composers!.filter(Boolean)" id="search-composers">
          <template #default="{ item: composer }">
            <ComposerCard :composer="composer" @click="navigateToComposer(composer.id)" />
          </template>
        </SearchSection>

      </div>
    </div>

    <TrackContextMenu ref="trackContextMenu" />
    <ContextMenu
      :visible="albumContextMenu.visible.value"
      :x="albumContextMenu.x.value"
      :y="albumContextMenu.y.value"
      :items="albumContextMenu.items.value"
      @close="albumContextMenu.close()"
    />
  </div>
</template>

<style scoped>
</style>
