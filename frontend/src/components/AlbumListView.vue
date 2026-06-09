<script setup lang="ts">
import { ref, watch } from 'vue'
import { Disc, Play, MoreVertical } from 'lucide-vue-next'
import type { AlbumDTO, Artist } from '../../bindings/airmedy/internal/domain/models'
import { buildArtworkUrl } from '@airmedy/utils'
import LazyImg from '@/components/LazyImg.vue'
import { useRowBackground } from '@/composables/useRowBackground'
import { useContextMenu } from '@/composables/useContextMenu'
import SortableHeaderCell from './SortableHeaderCell.vue'
import { useAlbumContextMenu } from '@/composables/useAlbumContextMenu'
import ContextMenu from './ContextMenu.vue'
import VirtualList from 'vue-virtual-sortable'

const GRID = '40px 1fr 1fr 80px 40px'
const ROW_HEIGHT = 56

type SortCol = 'title' | 'artist' | 'year'
type SortDir = 'asc' | 'desc'

const props = defineProps<{
  albums: AlbumDTO[]
  sortColumn: SortCol
  sortDir: SortDir
}>()

const emit = defineEmits<{
  'click': [id: string]
  'play': [id: string]
  'artist-click': [id: string]
  'update:sortColumn': [SortCol]
  'update:sortDir': [SortDir]
}>()

function cycleSort(col: SortCol) {
  if (props.sortColumn === col) {
    emit('update:sortDir', props.sortDir === 'asc' ? 'desc' : 'asc')
  } else {
    emit('update:sortColumn', col)
    emit('update:sortDir', 'asc')
  }
}

// VirtualList requires v-model (writable ref), not :model-value
const internalAlbums = ref<AlbumDTO[]>([...props.albums])
watch(() => props.albums, (v) => { internalAlbums.value = [...v] })

const contextMenu = useContextMenu()
const { buildMenuItems } = useAlbumContextMenu()

function onContextMenu(e: MouseEvent, album: AlbumDTO) {
  contextMenu.open(e, buildMenuItems(album))
}

const { rowBg } = useRowBackground()
</script>

<template>
  <div class="h-full flex flex-col overflow-hidden">
    <div class="flex-1 overflow-hidden">
      <div v-if="albums.length === 0" class="h-full flex flex-col items-center justify-center text-foreground opacity-60">
        <Disc class="w-12 h-12 mb-4 opacity-20" />
        <p>{{ $t('library.no_albums') }}</p>
      </div>

      <div v-else class="h-full flex flex-col overflow-hidden">
        <!-- Header -->
        <div class="overflow-hidden flex-shrink-0">
          <div
            class="grid sticky top-0 z-10 border-b border-foreground/[0.06] overflow-visible text-[10px] font-semibold text-foreground opacity-80 uppercase tracking-widest bg-background"
            :style="{ gridTemplateColumns: GRID, height: '40px' }"
          >
            <div class="sticky left-0 z-10 flex items-center justify-center relative bg-background">
              #
              <div class="absolute top-1/2 -translate-y-1/2 -right-2 h-4/5 w-4 z-20 flex items-center justify-center pointer-events-none">
                <div class="w-px h-full bg-foreground/[0.12]" />
              </div>
            </div>
            <SortableHeaderCell :active="sortColumn === 'title'" :dir="sortDir" @click="cycleSort('title')">
              <span class="truncate min-w-0 pointer-events-none">{{ $t('library.title') }}</span>
              <div class="absolute top-1/2 -translate-y-1/2 -right-2 h-4/5 w-4 z-20 flex items-center justify-center pointer-events-none">
                <div class="w-px h-full bg-foreground/[0.12]" />
              </div>
            </SortableHeaderCell>
            <SortableHeaderCell :active="sortColumn === 'artist'" :dir="sortDir" @click="cycleSort('artist')">
              <span class="truncate min-w-0 pointer-events-none">{{ $t('library.artist') }}</span>
              <div class="absolute top-1/2 -translate-y-1/2 -right-2 h-4/5 w-4 z-20 flex items-center justify-center pointer-events-none">
                <div class="w-px h-full bg-foreground/[0.12]" />
              </div>
            </SortableHeaderCell>
            <SortableHeaderCell :active="sortColumn === 'year'" :dir="sortDir" @click="cycleSort('year')">
              <span class="truncate min-w-0 pointer-events-none">{{ $t('library.year') }}</span>
              <div class="absolute top-1/2 -translate-y-1/2 -right-2 h-4/5 w-4 z-20 flex items-center justify-center pointer-events-none">
                <div class="w-px h-full bg-foreground/[0.12]" />
              </div>
            </SortableHeaderCell>
            <div class="sticky right-0 bg-background" />
          </div>
        </div>

        <!-- Rows -->
        <VirtualList
          v-model="internalAlbums"
          data-key="id"
          :size="ROW_HEIGHT"
          :sortable="false"
          class="flex-1 overflow-auto custom-scrollbar album-list transform-gpu"
        >
          <template #item="{ record: album, index }">
            <div :style="{ height: `${ROW_HEIGHT}px`, position: 'relative' }">
              <div
                class="absolute inset-x-0 grid items-center text-sm hover:bg-foreground/[0.04] group transition-colors h-full select-none cursor-pointer"
                :style="{ gridTemplateColumns: GRID, background: rowBg(index) }"
                @click="emit('click', album.id)"
                @dblclick="emit('play', album.id)"
                @contextmenu.prevent="onContextMenu($event, album)"
              >
                <!-- # / Play -->
                <div
                  class="sticky left-0 z-10 flex items-center justify-center h-full pointer-events-none"
                  :style="{ background: rowBg(index, true) }"
                >
                  <div class="text-foreground opacity-80 group-hover:hidden text-[11px]">{{ index + 1 }}</div>
                  <button
                    class="hidden group-hover:block text-primary hover:scale-110 transition-transform pointer-events-auto"
                    @click.stop="emit('play', album.id)"
                  >
                    <Play class="w-4 h-4 fill-current" />
                  </button>
                </div>

                <!-- Title + artwork -->
                <div class="px-2 font-medium truncate flex items-center gap-3 min-w-0">
                  <div class="w-8 h-8 bg-foreground/5 rounded flex-shrink-0 overflow-hidden">
                    <LazyImg
                      v-if="album.artwork_key"
                      :src="buildArtworkUrl(album.artwork_key, 'sm')"
                      :alt="album.title"
                      class="w-full h-full object-cover"
                    />
                    <div v-else class="w-full h-full flex items-center justify-center text-foreground opacity-40">
                      <Disc class="w-4 h-4" />
                    </div>
                  </div>
                  <span class="truncate">{{ album.title || $t('library.unknown_album') }}</span>
                </div>

                <!-- Artist -->
                <div class="px-2 text-foreground opacity-80 truncate flex items-center min-w-0">
                  <div class="truncate">
                    <template v-if="album.artists && album.artists.length > 0">
                      <span v-for="(artist, i) in (album.artists.filter(a => !!a) as Artist[])" :key="artist.id || i">
                        <span
                          :class="[artist.id ? 'hover:text-primary cursor-pointer transition-colors' : '']"
                          @click.stop="artist.id && emit('artist-click', artist.id)"
                        >{{ artist.name }}</span>
                        <span v-if="i < album.artists.filter(a => !!a).length - 1" class="mr-1">,</span>
                      </span>
                    </template>
                    <span v-else>{{ $t('library.unknown_artist') }}</span>
                  </div>
                </div>

                <!-- Year -->
                <div class="text-center text-foreground opacity-80 text-xs px-2">
                  {{ album.year || '' }}
                </div>

                <!-- Context menu -->
                <div
                  class="sticky right-0 z-10 flex items-center justify-end opacity-0 group-hover:opacity-100 pr-1 h-full"
                  :style="{ background: rowBg(index, true) }"
                >
                  <button
                    class="p-2 hover:bg-foreground/[0.08] rounded-full text-foreground opacity-50 hover:opacity-90 transition-colors"
                    @click.stop="onContextMenu($event, album)"
                  >
                    <MoreVertical class="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </template>
        </VirtualList>
      </div>
    </div>
  </div>

  <ContextMenu
    :visible="contextMenu.visible.value"
    :x="contextMenu.x.value"
    :y="contextMenu.y.value"
    :items="contextMenu.items.value"
    @close="contextMenu.close()"
  />
</template>

<style scoped>
.album-list {
  overflow: auto !important;
}
</style>
