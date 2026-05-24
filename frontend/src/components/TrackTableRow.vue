<script setup lang="ts">
import { Heart, Music, Play, MoreVertical, GripVertical } from 'lucide-vue-next'
import type { Artist, TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { formatTime, buildArtworkUrl, getTrackDisplayTitle } from '../lib/utils'
import LazyImg from '@/components/LazyImg.vue'
import PlayingBar from '@/components/PlayingBar.vue'
import { useFavoritesStore } from '../stores/favorites'
import { usePlayerStore } from '../stores/player'
import type { ColumnDef } from '@/composables/useTrackTableSettings'

const props = defineProps<{
  track: TrackDTO
  index: number
  currentIndex: number
  orderedVisibleColumns: ColumnDef[]
  gridTemplateColumns: string
  showArtwork?: boolean
  rowBg: (index: number, opaque?: boolean) => string
  variant?: 'default' | 'glass'
  isSelected?: boolean
}>()

const emit = defineEmits<{
  'play-track': [track: TrackDTO, index: number]
  'contextmenu': [e: MouseEvent, track: TrackDTO]
  'navigate-album': [id: string]
  'navigate-artist': [id: string]
  'click': [e: MouseEvent]
}>()

const playerStore = usePlayerStore()
const favoritesStore = useFavoritesStore()

const isCurrentTrack = (trackId: string) => playerStore.currentTrack?.id === trackId
</script>

<template>
  <div
    class="absolute inset-x-0 grid items-center text-sm hover:bg-foreground/[0.04] group transition-colors h-full select-none"
    :class="{ 'bg-primary/10 hover:bg-primary/[0.15]': isSelected }"
    :style="{
      gridTemplateColumns,
      background: isSelected ? undefined : rowBg(currentIndex),
    }"
    @click="emit('click', $event)"
    @contextmenu="emit('contextmenu', $event, track)"
    @dblclick="emit('play-track', track, index)"
  >
    <template v-for="(col, colIdx) in orderedVisibleColumns" :key="col.key">
      <!-- DnD Handle cell -->
      <div
        v-if="col.key === 'dnd'"
        class="sticky left-0 z-20 flex items-center justify-center h-full dnd-handle cursor-grab active:cursor-grabbing text-foreground opacity-20 group-hover:opacity-60 hover:text-primary transition-all px-2"
        :style="{ background: isSelected ? 'transparent' : rowBg(currentIndex, true) }"
      >
        <GripVertical class="w-4 h-4 pointer-events-none" />
      </div>

      <!-- Index cell -->
      <div
        v-else-if="col.key === 'index'"
        class="sticky z-10 flex items-center justify-center h-full pointer-events-none"
        :class="[orderedVisibleColumns[0].key === 'dnd' ? 'left-[32px]' : 'left-0']"
        :style="{ background: isSelected ? 'transparent' : rowBg(currentIndex, true) }"
      >
        <template v-if="isCurrentTrack(track.id)">
          <PlayingBar :is-playing="playerStore.isPlaying" />
        </template>
        <template v-else>
          <div class="text-foreground opacity-80 group-hover:hidden text-[11px]">{{ index + 1 }}</div>
          <button
            class="hidden group-hover:block text-primary hover:scale-110 transition-transform pointer-events-auto"
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
            class="w-full h-full flex items-center justify-center text-foreground opacity-50"
          >
            <Music class="w-4 h-4" />
          </div>
        </div>
        <span class="truncate" :class="{ 'text-primary': isCurrentTrack(track.id) }">
          {{ getTrackDisplayTitle(track) || $t('library.unknown_title') }}
        </span>
      </div>

      <!-- Duration cell -->
      <div
        v-else-if="col.key === 'duration'"
        class="text-center text-foreground opacity-80 text-xs px-2"
      >
        {{ formatTime(track.duration) }}
      </div>

      <!-- Artist cell -->
      <div
        v-else-if="col.key === 'artist'"
        class="text-foreground opacity-80 truncate flex items-center min-w-0 px-2"
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
        class="text-foreground opacity-80 truncate flex items-center min-w-0 px-2"
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
        class="text-center text-foreground opacity-80 text-xs px-2"
      >
        {{ track.year || '' }}
      </div>

      <!-- Genre cell -->
      <div
        v-else-if="col.key === 'genre'"
        class="text-foreground opacity-80 truncate text-xs px-2"
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
            : 'text-foreground opacity-40 group-hover:text-foreground opacity-60'"
        />
      </div>

      <!-- Play count cell -->
      <div
        v-else-if="col.key === 'play_count'"
        class="text-center text-foreground opacity-80 text-xs px-2"
      >
        {{ track.play_count || 0 }}
      </div>

      <!-- Disc number cell -->
      <div
        v-else-if="col.key === 'disc_number'"
        class="text-center text-foreground opacity-80 text-xs px-2"
      >
        {{ track.disc_number || '' }}
      </div>

      <!-- Track number cell -->
      <div
        v-else-if="col.key === 'track_number'"
        class="text-center text-foreground opacity-80 text-xs px-2"
      >
        {{ track.track_number || '' }}
      </div>

      <!-- Album artist cell -->
      <div
        v-else-if="col.key === 'album_artist'"
        class="text-foreground opacity-80 truncate flex items-center min-w-0 px-2"
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
        class="sticky right-0 z-10 flex items-center justify-end opacity-0 group-hover:opacity-100 pr-1"
        :style="{ background: isSelected ? 'transparent' : rowBg(currentIndex, true) }"
      >
        <button
          class="p-2 hover:bg-foreground/8 rounded-full text-foreground opacity-50 hover:text-foreground opacity-90 transition-colors"
          @click.stop="emit('contextmenu', $event, track)"
        >
          <MoreVertical class="w-4 h-4" />
        </button>
      </div>
    </template>
  </div>
</template>

