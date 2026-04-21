import { createApp } from 'vue'
import App from './App.vue'
import pinia from './stores'
import i18n from './locales'
import './assets/index.css'

const app = createApp(App)

app.use(pinia)
app.use(i18n)
app.mount('#app')
