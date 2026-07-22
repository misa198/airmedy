<script setup lang="ts">
import { onMounted, onUnmounted, ref, shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { Ghost, History, Music, Podium, Settings as SettingsIcon } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { Events } from '@wailsio/runtime'
import * as LibraryService from '../../../bindings/airmedy/internal/infra/wails/libraryservice'
import type { TrackDTO } from '../../../bindings/airmedy/internal/domain/models'
import { usePlayerStore } from '@/stores/player'
import HomeSection from '@/components/HomeSection.vue'
import TrackCard from '@/components/TrackCard.vue'
import TrackContextMenu from '@/components/TrackContextMenu.vue'

const { t } = useI18n()
const router = useRouter()
const playerStore = usePlayerStore()
const loading = ref(true)
const hasTracks = ref(false)
const recentlyPlayed = shallowRef<TrackDTO[]>([])
const mostListened = shallowRef<TrackDTO[]>([])
const leastListened = shallowRef<TrackDTO[]>([])
const contextMenu = ref<InstanceType<typeof TrackContextMenu> | null>(null)

async function fetchData() {
  loading.value = true
  try {
    hasTracks.value = (await LibraryService.GetTrackCount()) > 0
    if (!hasTracks.value) return
    const [recent, most, least] = await Promise.all([LibraryService.GetRecentlyPlayedTracks(28), LibraryService.GetMostListenedTracks(28), LibraryService.GetLeastListenedTracks(28)])
    recentlyPlayed.value = (recent || []).filter((track): track is TrackDTO => track !== null)
    mostListened.value = (most || []).filter((track): track is TrackDTO => track !== null)
    leastListened.value = (least || []).filter((track): track is TrackDTO => track !== null)
  } catch (error) { console.error('Failed to fetch home data:', error) } finally { loading.value = false }
}
const play = (track: TrackDTO) => playerStore.playTracks([track], 0)
const playAll = (tracks: TrackDTO[]) => { if (tracks.length) playerStore.playTracks(tracks, 0) }
const openTrack = (track: TrackDTO) => { if (track.album) router.push(`/albums/${track.album.id}`) }
const openArtist = (id: string) => router.push(`/artists/${id}`)
const openAlbum = (id: string) => router.push(`/albums/${id}`)
const onMenu = (event: MouseEvent, track: TrackDTO) => contextMenu.value?.open(event, track)
let off: (() => void) | undefined
onMounted(() => {
  fetchData()
  off = Events.On('library:sync-finished', (event: Events.WailsEvent) => {
    if (!(event.data as { background?: boolean })?.background) fetchData()
  })
})
onUnmounted(() => off?.())
</script>

<template>
  <div v-if="loading" class="h-full flex items-center justify-center"><div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary" /></div>
  <div v-else-if="!hasTracks" class="h-full flex flex-col items-center justify-center text-center">
    <Music class="w-12 h-12 text-white/40 mb-6" /><h2 class="text-3xl font-bold mb-3">{{ t('home.empty.title') }}</h2><p class="text-white/40 max-w-md mb-8">{{ t('home.empty.description') }}</p>
    <button class="flex items-center gap-2 px-6 py-3 bg-primary rounded-lg" @click="router.push('/settings/library')"><SettingsIcon class="w-4 h-4" />{{ t('home.empty.action') }}</button>
  </div>
  <div v-else class="space-y-16 pb-12 animate-in fade-in duration-700">
    <HomeSection :title="t('home.keep_listening')" :icon="History" :items="recentlyPlayed" id="carousel-recent" @play-all="playAll(recentlyPlayed)"><template #default="{ item }"><TrackCard :track="item" @play="play" @click="openTrack" @artist-click="openArtist" @album-click="openAlbum" @contextmenu="onMenu" /></template></HomeSection>
    <HomeSection :title="t('home.smart_mix')" :icon="Podium" :items="mostListened" id="carousel-most" @play-all="playAll(mostListened)"><template #default="{ item }"><TrackCard :track="item" @play="play" @click="openTrack" @artist-click="openArtist" @album-click="openAlbum" @contextmenu="onMenu" /></template></HomeSection>
    <HomeSection :title="t('home.forgotten')" :icon="Ghost" :items="leastListened" id="carousel-least" @play-all="playAll(leastListened)"><template #default="{ item }"><TrackCard :track="item" @play="play" @click="openTrack" @artist-click="openArtist" @album-click="openAlbum" @contextmenu="onMenu" /></template></HomeSection>
  </div>
  <TrackContextMenu ref="contextMenu" />
</template>
