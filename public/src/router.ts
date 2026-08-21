import { createRouter, createWebHistory } from 'vue-router'
import DownloadView from './views/DownloadView.vue'
import FaqView from './views/FaqView.vue'
import HomeView from './views/HomeView.vue'
import NotFoundView from './views/NotFoundView.vue'

export default createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView },
    { path: '/download/', component: DownloadView },
    { path: '/faq/:slug?', component: FaqView },
    { path: '/:pathMatch(.*)*', component: NotFoundView },
  ],
})
