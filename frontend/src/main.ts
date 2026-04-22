import { createApp } from 'vue'
import App from './App.vue'
import pinia from './stores'
import i18n from './locales'
import router from './router'
import VueVirtualScroller from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import './assets/index.css'

const app = createApp(App)

app.use(pinia)
app.use(i18n)
app.use(router)
app.use(VueVirtualScroller)
app.mount('#app')
