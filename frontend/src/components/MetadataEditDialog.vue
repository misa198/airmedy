<script setup lang="ts">
import { ref, watch, computed, onUnmounted } from 'vue'
import { Input } from '@/components/ui/input'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { MetadataUpdate } from '../../bindings/airmedy/internal/domain/models'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'
import * as LyricsService from '../../bindings/airmedy/internal/infra/wails/lyricsservice'
import { useI18n } from 'vue-i18n'
import { buildArtworkUrl } from '@/lib/utils'
import LazyImg from './LazyImg.vue'
import TabSwitcher from '@/components/ui/TabSwitcher.vue'
import Modal from '@/components/ui/Modal.vue'
import { ListMusic, Mic2 } from 'lucide-vue-next'

const { t } = useI18n()
const props = defineProps<{
  open: boolean
  track: TrackDTO | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: [trackId: string]
}>()

const saving = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const selectedImage = ref<string | null>(null)
const selectedImageData = ref<string | null>(null)

const activeTab = ref('info')
const tabOptions = computed(() => [
  { value: 'info', label: t('library.track_info'), icon: ListMusic },
  { value: 'lyrics', label: t('library.lyrics'), icon: Mic2 },
])

const form = ref<MetadataUpdate>(new MetadataUpdate())

const currentArtworkUrl = computed(() => {
  if (selectedImage.value) return selectedImage.value
  const key = props.track?.artwork_key || props.track?.album?.artwork_key
  return buildArtworkUrl(key, 'md')
})

watch(
  () => props.open,
  async (val) => {
    if (val && props.track) {
      activeTab.value = 'info'
      const t = props.track
      form.value = new MetadataUpdate({
        Title: t.title ?? '',
        Artist: t.raw_artist_names ?? t.artists?.filter((a): a is NonNullable<typeof a> => a != null).map(a => a.name).join('; ') ?? '',
        AlbumTitle: t.album?.title ?? '',
        Genre: t.raw_genre_names ?? t.genres?.filter((g): g is NonNullable<typeof g> => g != null).map(g => g.name).join('; ') ?? '',
        Composer: t.raw_composer_names ?? t.composers?.filter((c): c is NonNullable<typeof c> => c != null).map(c => c.name).join('; ') ?? '',
        Year: t.year ?? 0,
        TrackNumber: t.track_number ?? 0,
        TotalTracks: t.total_tracks ?? 0,
        DiscNumber: t.disc_number ?? 0,
        TotalDiscs: t.total_discs ?? 0,
        BPM: t.bpm ?? 0,
        Label: t.label ?? '',
        ISRC: t.isrc ?? '',
        Lyrics: '',
      })
      if (selectedImage.value) URL.revokeObjectURL(selectedImage.value)
      selectedImage.value = null
      selectedImageData.value = null
      error.value = ''

      try {
        const lyric = await LyricsService.GetLyrics(props.track.id)
        if (lyric && lyric.meta_content) {
          form.value.Lyrics = lyric.meta_content
        }
      } catch (e) {
        console.error('Failed to fetch lyrics:', e)
      }
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  if (selectedImage.value) URL.revokeObjectURL(selectedImage.value)
})

function setInt(key: keyof MetadataUpdate, val: string) {
  ;(form.value as Record<string, unknown>)[key] = parseInt(val) || 0
}

function triggerFileSelect() {
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (event) => {
    const img = new Image()
    img.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = img.width
      canvas.height = img.height
      const ctx = canvas.getContext('2d')
      if (!ctx) return
      ctx.drawImage(img, 0, 0)
      
      // Convert to JPEG
      canvas.toBlob((blob) => {
        if (!blob) return
        const reader2 = new FileReader()
        reader2.onload = (e2) => {
          const arrayBuffer = e2.target?.result as ArrayBuffer
          const bytes = new Uint8Array(arrayBuffer)
          let binary = ''
          for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i])
          }
          selectedImageData.value = btoa(binary)
          if (selectedImage.value) URL.revokeObjectURL(selectedImage.value)
          selectedImage.value = URL.createObjectURL(blob)
        }
        reader2.readAsArrayBuffer(blob)
      }, 'image/jpeg', 0.9)
    }
    img.src = event.target?.result as string
  }
  reader.readAsDataURL(file)
}

async function save() {
  if (!props.track) return
  saving.value = true
  error.value = ''
  try {
    const update = { ...form.value }
    if (selectedImageData.value) {
      update.ArtworkData = selectedImageData.value
      update.ArtworkMIME = 'image/jpeg'
    }
    await LibraryService.UpdateTrackMetadata(props.track.id, update)
    emit('saved', props.track.id)
    emit('update:open', false)
  } catch (e) {
    error.value = t('library.save_metadata_error')
  } finally {
    saving.value = false
  }
}

function cancel() {
  emit('update:open', false)
}
</script>

<template>
  <Modal :open="open" :title="t('library.edit_metadata')" width-class="w-[480px]" @close="cancel">
    <template #default>
      <div class="flex items-center justify-between mb-4 -mt-10">
        <div />
        <TabSwitcher v-model="activeTab" :options="tabOptions" mandatory />
      </div>

      <div class="max-h-[70vh] overflow-y-auto custom-scrollbar -mx-1 px-1">
        <div v-show="activeTab === 'info'">
            <div class="flex gap-5 mb-5">
              <div class="relative group cursor-pointer w-32 h-32 flex-shrink-0" @click="triggerFileSelect">
                <div class="w-full h-full rounded-lg overflow-hidden bg-foreground/[0.05] ring-1 ring-border-glass">
                  <LazyImg v-if="currentArtworkUrl" :src="currentArtworkUrl" class="w-full h-full object-cover" />
                  <div v-else class="w-full h-full flex items-center justify-center opacity-20">
                    <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="18" height="18" x="3" y="3" rx="2" ry="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/></svg>
                  </div>
                </div>
                <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center rounded-lg">
                  <span class="text-[10px] font-medium text-white uppercase tracking-wider">{{ t('common.change') }}</span>
                </div>
                <input
                  ref="fileInput"
                  type="file"
                  class="hidden"
                  accept="image/png,image/jpeg,image/jpg"
                  @change="onFileChange"
                />
              </div>

              <div class="flex-grow space-y-3">
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.title') }}</label>
                  <Input
                    v-model="form.Title"
                    :placeholder="t('library.title')"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                  />
                </div>
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.artist') }}</label>
                  <Input
                    v-model="form.Artist"
                    :placeholder="t('library.artist')"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                  />
                </div>
              </div>
            </div>

            <div class="space-y-3">
              <div>
                <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.album') }}</label>
                <Input
                  v-model="form.AlbumTitle"
                  :placeholder="t('library.album')"
                  class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                />
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.genre') }}</label>
                  <Input
                    v-model="form.Genre"
                    :placeholder="t('library.genre')"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                  />
                </div>
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.composer') }}</label>
                  <Input
                    v-model="form.Composer"
                    :placeholder="t('library.composer')"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                  />
                </div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('track_info.label') }}</label>
                  <Input
                    v-model="form.Label"
                    :placeholder="t('track_info.label')"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                  />
                </div>
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('track_info.isrc') }}</label>
                  <Input
                    v-model="form.ISRC"
                    :placeholder="t('track_info.isrc')"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                  />
                </div>
              </div>
              <div class="grid grid-cols-3 gap-3">
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.year') }}</label>
                  <Input
                    :model-value="form.Year.toString()"
                    :placeholder="t('library.year')"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                    @update:model-value="setInt('Year', $event as string)"
                  />
                </div>
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.track') }}</label>
                  <Input
                    :model-value="form.TrackNumber.toString()"
                    placeholder="0"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                    @update:model-value="setInt('TrackNumber', $event as string)"
                  />
                </div>
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('track_info.bpm') }}</label>
                  <Input
                    :model-value="form.BPM.toString()"
                    placeholder="0"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                    @update:model-value="setInt('BPM', $event as string)"
                  />
                </div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.total') }}</label>
                  <Input
                    :model-value="form.TotalTracks.toString()"
                    placeholder="0"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                    @update:model-value="setInt('TotalTracks', $event as string)"
                  />
                </div>
              </div>
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.disc') }}</label>
                  <Input
                    :model-value="form.DiscNumber.toString()"
                    placeholder="0"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                    @update:model-value="setInt('DiscNumber', $event as string)"
                  />
                </div>
                <div>
                  <label class="block text-xs text-foreground/80 mb-1 font-medium">{{ t('library.total_discs') }}</label>
                  <Input
                    :model-value="form.TotalDiscs.toString()"
                    placeholder="0"
                    class="bg-foreground/[0.07] border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20"
                    @update:model-value="setInt('TotalDiscs', $event as string)"
                  />
                </div>
              </div>
            </div>
          </div>

          <div v-show="activeTab === 'lyrics'" class="space-y-3 h-[400px] flex flex-col">
            <label class="block text-xs text-foreground/80 font-medium">{{ t('library.lyrics') }}</label>
            <textarea
              v-model="form.Lyrics"
              :placeholder="t('library.lyrics_placeholder')"
              class="flex-grow w-full bg-foreground/[0.07] border border-foreground/20 text-foreground placeholder:text-foreground/40 focus-visible:ring-primary/20 rounded-lg p-3 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-primary/20"
            ></textarea>
          </div>

      </div>

      <p v-if="error" class="mt-3 text-xs text-red-400">{{ error }}</p>

      <div class="flex justify-end gap-2 mt-5">
        <button
          class="px-3 py-1.5 text-sm text-foreground opacity-70 hover:text-foreground rounded-lg hover:bg-foreground/[0.05] transition-colors"
          @click="cancel"
        >{{ t('common.cancel') }}</button>
        <button
          class="px-3 py-1.5 text-sm text-primary-foreground bg-primary hover:bg-primary/90 rounded-lg transition-colors font-medium disabled:opacity-40"
          :disabled="saving"
          @click="save"
        >{{ saving ? t('common.saving') : t('common.save') }}</button>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
</style>
