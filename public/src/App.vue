<script setup lang="ts">
import { Menu, Moon, Sun, X } from '@lucide/vue'
import { ref, watch } from 'vue'
import { RouterLink, RouterView } from 'vue-router'

const menuOpen = ref(false)
const theme = ref<'dark' | 'light'>(localStorage.getItem('airmedy-theme') === 'light' ? 'light' : 'dark')

watch(theme, value => {
  document.body.className = value
  localStorage.setItem('airmedy-theme', value)
}, { immediate: true })

function closeMenu() {
  menuOpen.value = false
}
</script>

<template>
  <header class="header">
    <nav class="nav container">
      <RouterLink to="/" class="logo" @click="closeMenu">
        <img src="/airmedy.png" alt="Airmedy logo" class="logo-img" />
        <span>Airmedy</span>
      </RouterLink>
      <ul class="nav-links" :class="{ open: menuOpen }">
        <li><a href="/#features" @click="closeMenu">Features</a></li>
        <li><a href="/#screenshots" @click="closeMenu">Screenshots</a></li>
        <li><RouterLink to="/faq/" @click="closeMenu">FAQ</RouterLink></li>
        <li><button class="theme-toggle" :aria-label="`Use ${theme === 'dark' ? 'light' : 'dark'} theme`" @click="theme = theme === 'dark' ? 'light' : 'dark'"><Sun v-if="theme === 'dark'" :size="18" /><Moon v-else :size="18" /></button></li>
        <li><RouterLink to="/download/" class="btn btn-primary" @click="closeMenu">Download</RouterLink></li>
      </ul>
      <button class="mobile-menu-toggle" :aria-expanded="menuOpen" aria-label="Toggle menu" @click="menuOpen = !menuOpen"><X v-if="menuOpen" :size="24" /><Menu v-else :size="24" /></button>
    </nav>
  </header>
  <RouterView />
  <footer class="footer"><div class="container footer-content"><p>© 2026 misa198</p><div class="footer-links"><a href="https://github.com/misa198/airmedy">GitHub</a><a href="https://github.com/misa198/airmedy/issues">Report an issue</a></div></div></footer>
</template>
