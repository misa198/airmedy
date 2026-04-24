<script setup lang="ts">
import { ref, watch } from 'vue'
import { Input } from '@/components/ui/input'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { MetadataUpdate } from '../../bindings/airmedy/internal/domain/models'
import * as LibraryService from '../../bindings/airmedy/internal/infra/wails/libraryservice'

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

const form = ref<MetadataUpdate>(new MetadataUpdate())

watch(
  () => props.open,
  (val) => {
    if (val && props.track) {
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
      })
      error.value = ''
    }
  },
  { immediate: true },
)

function setInt(key: keyof MetadataUpdate, val: string) {
  ;(form.value as Record<string, unknown>)[key] = parseInt(val) || 0
}

async function save() {
  if (!props.track) return
  saving.value = true
  error.value = ''
  try {
    await LibraryService.UpdateTrackMetadata(props.track.id, form.value)
    emit('saved', props.track.id)
    emit('update:open', false)
  } catch (e) {
    error.value = 'Failed to save metadata. Check file permissions.'
  } finally {
    saving.value = false
  }
}

function cancel() {
  emit('update:open', false)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center"
        @click.self="cancel"
      >
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="cancel" />
        <div
          class="relative z-10 w-[480px] max-h-[85vh] overflow-y-auto rounded-xl bg-[#1A1A1A] ring-1 ring-white/[0.08] shadow-2xl p-5"
          @keydown.esc="cancel"
        >
          <h3 class="text-sm font-semibold text-white mb-4">Edit Metadata</h3>

          <div class="space-y-3">
            <div>
              <label class="block text-xs text-white/40 mb-1">Title</label>
              <Input
                v-model="form.Title"
                placeholder="Title"
                class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
              />
            </div>
            <div>
              <label class="block text-xs text-white/40 mb-1">Artist</label>
              <Input
                v-model="form.Artist"
                placeholder="Artist"
                class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
              />
            </div>
            <div>
              <label class="block text-xs text-white/40 mb-1">Album</label>
              <Input
                v-model="form.AlbumTitle"
                placeholder="Album"
                class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
              />
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs text-white/40 mb-1">Genre</label>
                <Input
                  v-model="form.Genre"
                  placeholder="Genre"
                  class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
                />
              </div>
              <div>
                <label class="block text-xs text-white/40 mb-1">Composer</label>
                <Input
                  v-model="form.Composer"
                  placeholder="Composer"
                  class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
                />
              </div>
            </div>
            <div class="grid grid-cols-3 gap-3">
              <div>
                <label class="block text-xs text-white/40 mb-1">Year</label>
                <Input
                  :model-value="form.Year.toString()"
                  placeholder="Year"
                  class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
                  @update:model-value="setInt('Year', $event as string)"
                />
              </div>
              <div>
                <label class="block text-xs text-white/40 mb-1">Track</label>
                <Input
                  :model-value="form.TrackNumber.toString()"
                  placeholder="0"
                  class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
                  @update:model-value="setInt('TrackNumber', $event as string)"
                />
              </div>
              <div>
                <label class="block text-xs text-white/40 mb-1">Total</label>
                <Input
                  :model-value="form.TotalTracks.toString()"
                  placeholder="0"
                  class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
                  @update:model-value="setInt('TotalTracks', $event as string)"
                />
              </div>
            </div>
            <div class="grid grid-cols-2 gap-3">
              <div>
                <label class="block text-xs text-white/40 mb-1">Disc</label>
                <Input
                  :model-value="form.DiscNumber.toString()"
                  placeholder="0"
                  class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
                  @update:model-value="setInt('DiscNumber', $event as string)"
                />
              </div>
              <div>
                <label class="block text-xs text-white/40 mb-1">Total Discs</label>
                <Input
                  :model-value="form.TotalDiscs.toString()"
                  placeholder="0"
                  class="bg-white/[0.05] border-white/[0.08] text-white placeholder:text-white/20 focus-visible:ring-white/20"
                  @update:model-value="setInt('TotalDiscs', $event as string)"
                />
              </div>
            </div>
          </div>

          <p v-if="error" class="mt-3 text-xs text-red-400">{{ error }}</p>

          <div class="flex justify-end gap-2 mt-5">
            <button
              class="px-3 py-1.5 text-sm text-white/50 hover:text-white rounded-lg hover:bg-white/[0.05] transition-colors"
              @click="cancel"
            >Cancel</button>
            <button
              class="px-3 py-1.5 text-sm text-white bg-white/[0.12] hover:bg-white/[0.18] rounded-lg transition-colors font-medium disabled:opacity-40"
              :disabled="saving"
              @click="save"
            >{{ saving ? 'Saving…' : 'Save' }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
