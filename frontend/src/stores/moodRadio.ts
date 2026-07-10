import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import * as MoodRadioService from '../../bindings/airmedy/internal/infra/wails/moodradioservice'
import * as PlayerService from '../../bindings/airmedy/internal/infra/wails/playerservice'
import type { TrackDTO } from '../../bindings/airmedy/internal/domain/models'
import { usePlayerStore } from './player'
import { useAppStore } from './app'

const REFILL_THRESHOLD = 3
const SEED_BATCH_SIZE = 15

export const useMoodRadioStore = defineStore('moodRadio', () => {
  const active = ref(false)
  const seedTrackId = ref<string | null>(null)
  let refilling = false

  async function start(seedTrack: TrackDTO) {
    seedTrackId.value = seedTrack.id
    active.value = true
    try {
      // GenerateMoodRadio/FindSimilar excludes the seed track from its results
      // (it's always excluded as a candidate for itself), so it has to be
      // prepended here — otherwise the track the user picked never plays,
      // a similar track plays first instead.
      const similar = await fetchSimilar(seedTrack.id, [])
      const tracks = [seedTrack, ...similar.filter(t => t.id !== seedTrack.id)]
      const playerStore = usePlayerStore()
      await playerStore.playTracks(tracks, 0, true)
    } catch (e) {
      // Seeding failed (e.g. seed track not yet analyzed, backend error) —
      // reset instead of leaving the radio "on" over a queue that never
      // populated, which would keep refillIfNeeded firing against nothing.
      console.error('Mood Radio start failed', e)
      stop()
    }
  }

  function stop() {
    active.value = false
    seedTrackId.value = null
  }

  async function fetchSimilar(trackId: string, excludeTrackIDs: string[]): Promise<TrackDTO[]> {
    const result = await MoodRadioService.GenerateMoodRadio(trackId, excludeTrackIDs, SEED_BATCH_SIZE)
    return result.filter(Boolean) as TrackDTO[]
  }

  async function refillIfNeeded() {
    if (!active.value || refilling) return
    // Library analysis feeds GenerateMoodRadio's similarity search; if the user
    // disables it mid-session (the context menu already hides the "Start
    // Mood Radio" entry in that case), the queue must stop silently growing
    // instead of continuing to fetch "similar" tracks from stale data.
    if (!useAppStore().libraryAnalysisEnabled) {
      stop()
      return
    }
    const playerStore = usePlayerStore()
    // queue.length is the full backend list (it doesn't shrink as tracks
    // play), so "remaining" has to be measured from the current track's
    // position, not raw length — otherwise jumping straight to (or near)
    // the last queued track never trips a refill.
    const currentIndex = playerStore.currentTrack
      ? playerStore.queue.findIndex(t => t.id === playerStore.currentTrack!.id)
      : -1
    const remaining = currentIndex === -1
      ? playerStore.queue.length
      : playerStore.queue.length - 1 - currentIndex
    if (remaining >= REFILL_THRESHOLD) return
    const seed = playerStore.currentTrack?.id ?? seedTrackId.value
    if (!seed) return

    refilling = true
    try {
      const existingSet = new Set(playerStore.queue.map(t => t.id))
      const next = await fetchSimilar(seed, [...existingSet])
      if (next.length) {
        // AppendTracks adds straight to the tail in one mutation. The
        // previous approach (PlayNextTracks then ReorderQueueIDs) fired two
        // separate queue-updated events — insert-after-current followed by
        // a reorder to the tail — which visibly reshuffled every row from
        // the current track to the end twice in a row.
        await PlayerService.AppendTracks(next)
      }
    } catch (e) {
      console.error('Mood Radio refill failed', e)
    } finally {
      refilling = false
    }
  }

  let _stopWatch: (() => void) | null = null
  let _initialized = false

  let _offLibraryAnalysis: (() => void) | null = null

  function init() {
    if (_initialized) return
    _initialized = true
    const playerStore = usePlayerStore()
    _stopWatch = watch(
      [() => playerStore.queue.length, () => playerStore.currentTrack?.id],
      () => refillIfNeeded(),
    )
    // Turning off library analysis mid-session must end Mood Radio right
    // away — waiting for the next refill check (queue.length/currentTrack
    // watch above) leaves the radio icon showing until the user happens to
    // skip near the end of the queue.
    _offLibraryAnalysis = watch(
      () => useAppStore().libraryAnalysisEnabled,
      (enabled) => {
        if (!enabled && active.value) stop()
      },
    )
  }

  function dispose() {
    _stopWatch?.()
    _stopWatch = null
    _offLibraryAnalysis?.()
    _offLibraryAnalysis = null
    _initialized = false
    active.value = false
  }

  return {
    active,
    seedTrackId,
    start,
    stop,
    init,
    dispose,
  }
})
