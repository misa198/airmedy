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
    path: '/albums/:id',
    name: 'album-detail',
    component: () => import('../views/AlbumDetailView.vue')
  },
  {
    path: '/artists',
    name: 'artists',
    component: () => import('../views/ArtistsView.vue'),
    children: [
      {
        path: ':id',
        name: 'artist-detail',
        component: () => import('../views/ArtistDetailView.vue')
      }
    ]
  },
  {
    path: '/tracks',
    name: 'tracks',
    component: () => import('../views/TracksView.vue')
  },
  {
    path: '/genres',
    name: 'genres',
    component: () => import('../views/GenresView.vue'),
    children: [
      {
        path: ':id',
        name: 'genre-detail',
        component: () => import('../views/GenreDetailView.vue')
      }
    ]
  },
  {
    path: '/composers',
    name: 'composers',
    component: () => import('../views/ComposersView.vue'),
    children: [
      {
        path: ':id',
        name: 'composer-detail',
        component: () => import('../views/ComposerDetailView.vue')
      }
    ]
  },
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/SearchView.vue')
  },
  {
    path: '/playlists/:id',
    name: 'playlist-detail',
    component: () => import('../views/PlaylistDetailView.vue')
  },
  {
    path: '/settings/:category?',
    name: 'settings',
    component: () => import('../views/SettingsView.vue'),
    props: true
  },
  {
    path: '/mini-player',
    name: 'mini-player',
    component: () => import('../views/MiniPlayerWindowView.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
