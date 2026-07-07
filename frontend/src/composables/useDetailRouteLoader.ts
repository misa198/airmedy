import { onActivated, onMounted } from 'vue'
import { onBeforeRouteUpdate, useRoute } from 'vue-router'

// Wires the common "load detail page by :id route param" pattern shared by
// Album/Artist/Genre/Composer/Playlist detail views. Three distinct
// navigation shapes all need to trigger a (re)load:
//
// 1. First-ever visit: onMounted.
// 2. Same route, id changes in place (e.g. album A -> album B directly):
//    onBeforeRouteUpdate.
// 3. Navigate away to an unrelated route, then back to this route (possibly
//    with a different id): MainLayout keeps this component alive via
//    <KeepAlive>, cached by component *type* — not by route/id. Re-entering
//    is a fresh "enter" transition (not an "update"), so onBeforeRouteUpdate
//    never fires, and KeepAlive suppresses onMounted from firing again.
//    onActivated is the only hook that covers this case (it also fires on
//    the very first mount when inside a <KeepAlive>, alongside onMounted —
//    a harmless duplicate fetch, not a correctness issue).
//
// Staleness (dropping a response superseded by a newer navigation) is NOT
// handled here — comparing against the reactive route.params.id is racy: for
// a fast-resolving load, the fetch can finish before vue-router has finished
// updating route.params, making a perfectly fresh response look stale. Each
// view instead tracks its own synchronous "latest requested id" token, set as
// the first line of its load function, before any await.
export function useDetailRouteLoader(load: (id: string) => void) {
  const route = useRoute()

  onMounted(() => {
    const id = route.params.id as string
    if (id) load(id)
  })

  onActivated(() => {
    const id = route.params.id as string
    if (id) load(id)
  })

  onBeforeRouteUpdate((to) => {
    const id = to.params.id as string
    if (id) load(id)
  })
}
