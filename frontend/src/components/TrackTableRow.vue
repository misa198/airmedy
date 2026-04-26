<script setup lang="ts">
import { Heart, Music, Play, MoreVertical } from 'lucide-vue-next'
import type { Artist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { formatTime, buildArtworkUrl } from '../lib/utils'
import LazyImg from '@/components/LazyImg.vue'
import { useFavoritesStore } from '../stores/favorites'
import { usePlayerStore } from '../stores/player'
import type { ColumnDef } from '@/composables/useTrackTableSettings'

const props = defineProps<{
  track: TrackDTO
  index: number
  top: number
  orderedVisibleColumns: ColumnDef[]
  gridTemplateColumns: string
  showArtwork?: boolean
  rowBg: (index: number, opaque?: boolean) => string
  variant?: 'default' | 'glass'
}>()

const emit = defineEmits<{
  'play-track': [track: TrackDTO, index: number]
  'contextmenu': [e: MouseEvent, track: TrackDTO]
  'navigate-album': [id: string]
  'navigate-artist': [id: string]
}>()

const playerStore = usePlayerStore()
const favoritesStore = useFavoritesStore()

const isCurrentTrack = (trackId: string) => playerStore.currentTrack?.id === trackId
</script>

<template>
  <div
    class="grid absolute left-0 right-0 items-center text-sm hover:bg-foreground/[0.04] group transition-colors h-[56px]"
    :style="{
      top: top + 'px',
      gridTemplateColumns,
      background: rowBg(index),
    }"
    @contextmenu="emit('contextmenu', $event, track)"
    @dblclick="emit('play-track', track, index)"
  >
    <template v-for="col in orderedVisibleColumns" :key="col.key">
      <!-- Index cell -->
      <div
        v-if="col.key === 'index'"
        class="sticky left-0 z-[5] flex items-center justify-center h-full"
        :style="{ background: rowBg(index, true) }"
      >
        <template v-if="isCurrentTrack(track.id)">
          <div class="flex items-end gap-[2px] h-3 w-3">
            <div
              v-for="i in 3"
              :key="i"
              class="w-full h-full bg-primary origin-bottom transition-transform duration-500 ease-in-out"
              :class="playerStore.isPlaying ? `animate-playing-bar-${i}` : ''"
              :style="{
                transform: !playerStore.isPlaying
                  ? (i === 1 ? 'scaleY(0.3)' : i === 2 ? 'scaleY(1.0)' : 'scaleY(0.6)')
                  : undefined,
              }"
            />
          </div>
        </template>
        <template v-else>
          <div class="text-foreground/80 group-hover:hidden">{{ index + 1 }}</div>
          <button
            class="hidden group-hover:block text-primary hover:scale-110 transition-transform"
            @click="emit('play-track', track, index)"
          >
            <Play class="w-4 h-4 fill-current" />
          </button>
        </template>
      </div>

      <!-- Title cell -->
      <div
        v-else-if="col.key === 'title'"
        class="font-medium truncate flex items-center gap-3 min-w-0 px-2"
      >
        <div
          v-if="showArtwork"
          class="w-8 h-8 bg-foreground/5 rounded flex-shrink-0 overflow-hidden"
        >
          <LazyImg
            v-if="track.artwork_key"
            :src="buildArtworkUrl(track.artwork_key, 'sm')"
            class="w-full h-full object-cover"
          />
          <div
            v-else
            class="w-full h-full flex items-center justify-center text-foreground/30"
          >
            <Music class="w-4 h-4" />
          </div>
        </div>
        <span class="truncate" :class="{ 'text-primary': isCurrentTrack(track.id) }">
          {{ track.title || $t('library.unknown_title') }}
        </span>
      </div>

      <!-- Duration cell -->
      <div
        v-else-if="col.key === 'duration'"
        class="text-center text-foreground/80 text-xs px-2"
      >
        {{ formatTime(track.duration) }}
      </div>

      <!-- Artist cell -->
      <div
        v-else-if="col.key === 'artist'"
        class="text-foreground/80 truncate flex items-center min-w-0 px-2"
      >
        <div class="truncate">
          <template v-if="track.artists && track.artists.length > 0">
            <span
              v-for="(artist, i) in (track.artists.filter(a => !!a) as Artist[])"
              :key="artist.id || i"
            >
              <span
                :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                @click.stop="artist.id && emit('navigate-artist', artist.id)"
              >{{ artist.name }}</span>
              <span v-if="i < track.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
            </span>
          </template>
          <span v-else>{{ track.raw_artist_names || $t('library.unknown_artist') }}</span>
        </div>
      </div>

      <!-- Album cell -->
      <div
        v-else-if="col.key === 'album'"
        class="text-foreground/80 truncate flex items-center min-w-0 px-2"
      >
        <span
          class="truncate hover:text-primary transition-colors cursor-pointer"
          @click.stop="track.album?.id && emit('navigate-album', track.album.id)"
        >
          {{ track.album?.title || $t('library.unknown_album') }}
        </span>
      </div>

      <!-- Year cell -->
      <div
        v-else-if="col.key === 'year'"
        class="text-center text-foreground/80 text-xs px-2"
      >
        {{ track.year || '' }}
      </div>

      <!-- Genre cell -->
      <div
        v-else-if="col.key === 'genre'"
        class="text-foreground/80 truncate text-xs px-2"
      >
        {{ track.raw_genre_names || '' }}
      </div>

      <!-- Favorite cell -->
      <div
        v-else-if="col.key === 'favorite'"
        class="flex items-center justify-center px-2"
      >
        <Heart
          class="w-3.5 h-3.5 transition-colors"
          :class="favoritesStore.isFavorite(track)
            ? 'text-primary fill-current'
            : 'text-foreground/20 group-hover:text-foreground/40'"
        />
      </div>

      <!-- Play count cell -->
      <div
        v-else-if="col.key === 'play_count'"
        class="text-center text-foreground/80 text-xs px-2"
      >
        {{ track.play_count || 0 }}
      </div>

      <!-- Disc number cell -->
      <div
        v-else-if="col.key === 'disc_number'"
        class="text-center text-foreground/80 text-xs px-2"
      >
        {{ track.disc_number || '' }}
      </div>

      <!-- Track number cell -->
      <div
        v-else-if="col.key === 'track_number'"
        class="text-center text-foreground/80 text-xs px-2"
      >
        {{ track.track_number || '' }}
      </div>

      <!-- Album artist cell -->
      <div
        v-else-if="col.key === 'album_artist'"
        class="text-foreground/80 truncate flex items-center min-w-0 px-2"
      >
        <div class="truncate">
          <template v-if="track.album_artists && track.album_artists.length > 0">
            <span
              v-for="(artist, i) in (track.album_artists.filter(a => !!a) as Artist[])"
              :key="artist.id || i"
            >
              <span
                :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                @click.stop="artist.id && emit('navigate-artist', artist.id)"
              >{{ artist.name }}</span>
              <span v-if="i < track.album_artists.filter(a => !!a).length - 1" class="mr-1">,</span>
            </span>
          </template>
          <span v-else>{{ track.raw_album_artist_names || '' }}</span>
        </div>
      </div>

      <!-- Context menu cell -->
      <div
        v-else-if="col.key === 'context_menu'"
        class="sticky right-0 z-[5] flex items-center justify-end opacity-0 group-hover:opacity-100 pr-1"
        :style="{ background: rowBg(index, true) }"
      >
        <button
          class="p-2 hover:bg-foreground/8 rounded-full text-foreground/30 hover:text-foreground/70 transition-colors"
          @click.stop="emit('contextmenu', $event, track)"
        >
          <MoreVertical class="w-4 h-4" />
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
@keyframes playing-bar-1 {
  0%, 100% { transform: scaleY(0.3); }
  50% { transform: scaleY(0.8); }
}
@keyframes playing-bar-2 {
  0%, 100% { transform: scaleY(1.0); }
  50% { transform: scaleY(0.4); }
}
@keyframes playing-bar-3 {
  0%, 100% { transform: scaleY(0.6); }
  50% { transform: scaleY(0.9); }
}

.animate-playing-bar-1 { animation: playing-bar-1 0.8s ease-in-out infinite; }
.animate-playing-bar-2 { animation: playing-bar-2 0.6s ease-in-out infinite; }
.animate-playing-bar-3 { animation: playing-bar-3 0.7s ease-in-out infinite; }
</style>
