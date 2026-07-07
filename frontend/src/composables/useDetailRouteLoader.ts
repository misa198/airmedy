import { onMounted } from 'vue'
import { onBeforeRouteUpdate, useRoute } from 'vue-router'

// Wires the common "load detail page by :id route param" pattern shared by
// Album/Artist/Genre/Composer/Playlist detail views: fetch on mount, refetch
// via onBeforeRouteUpdate (scoped to this route so it never fires from an
// unrelated navigation while KeepAlive keeps the instance around), and expose
// isStale() so in-flight loads can bail if a newer navigation has since
// superseded them.
export function useDetailRouteLoader(load: (id: string) => void) {
  const route = useRoute()

  onMounted(() => {
    const id = route.params.id as string
    if (id) load(id)
  })

  onBeforeRouteUpdate((to) => {
    const id = to.params.id as string
    if (id) load(id)
  })

  const isStale = (id: string) => (route.params.id as string) !== id

  return { isStale }
}
