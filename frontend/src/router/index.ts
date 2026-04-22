import { createRouter, createWebHashHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const routes = [
  {
    path: '/',
    name: 'home',
    component: HomeView
  },
  {
    path: '/recently-added',
    name: 'recently-added',
    component: () => import('../views/RecentlyAddedView.vue')
  },
  {
    path: '/albums',
    name: 'albums',
    component: () => import('../views/AlbumsView.vue')
  },
  {
    path: '/artists',
    name: 'artists',
    component: () => import('../views/ArtistsView.vue')
  },
  {
    path: '/tracks',
    name: 'tracks',
    component: () => import('../views/TracksView.vue')
  },
  {
    path: '/genres',
    name: 'genres',
    component: () => import('../views/GenresView.vue')
  },
  {
    path: '/composers',
    name: 'composers',
    component: () => import('../views/ComposersView.vue')
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/SearchView.vue')
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/SettingsView.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
