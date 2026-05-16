import { createApp } from 'vue'
import App from './App.vue'
import pinia from './stores'
import i18n from './locales'
import router from './router'
import VirtualList from 'vue-virtual-sortable'
import VueVirtualScroller from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import './assets/index.css'

window.addEventListener('contextmenu', (e) => e.preventDefault())

const app = createApp(App)

app.use(pinia)
app.use(i18n)
app.use(router)
app.use(VueVirtualScroller)
app.component('VirtualList', VirtualList)
app.mount('#app')
