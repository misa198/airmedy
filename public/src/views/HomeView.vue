<script setup lang="ts">
const features = [
  ['Your whole library', 'Add any folder and Airmedy scans it instantly, even with tens of thousands of tracks.'],
  ['Lyrics that follow', 'Synced lyrics scroll line-by-line as the song plays.'],
  ['Fast Search', 'Find any track, album, or artist in milliseconds.'],
  ['Playlists', 'Create, import, export, and browse playlists.'],
  ['Mood Radio & Smart Playlists', 'Build mood-based or rule-based playlists automatically.'],
  ['10-band Equalizer', 'Tune the sound to your headphones on every platform.'],
  ['Crossfade', 'Blend the current track into the next with an equal-power fade.'],
  ['Media Keys & Tray', 'Control playback via keyboard, lock screen, or tray.'],
  ['Last.fm Scrobbling', 'Sync listening history and loved tracks automatically.'],
  ['Mini & Fullscreen', 'Switch between immersive fullscreen and compact mini player.'],
  ['Remote Control', 'Control playback from any device on your network.'],
  ['Mobile Sync', 'Take your library, playback, and lyrics with you on mobile.'],
]

const screenshots = [
  ['Library Explorer', 'Beautiful glass-morphic interface that highlights your album art and metadata.', '/album-grid.png'],
  ['Synced Lyrics', 'Immersive fullscreen mode with lyrics that move with the music.', '/fullscreen-player-1.webp'],
  ['Compact Player', 'A tiny, versatile mini-player that stays out of your way.', '/mini-player-2.webp'],
  ['Artist Insights', 'Deep dive into your favorite artists with discography and bios.', '/artist.png'],
  ['Remote Control', 'Control your music from any phone or browser on your local network.', '/remote.webp'],
  ['Airmedy Mobile', 'Browse your library, control playback, and follow lyrics from your phone.', '/mobiles.webp'],
]
</script>

<template>
  <main>
    <section class="hero container">
      <div class="hero-content">
        <h1 class="hero-title">Your music, your way.</h1>
        <p class="hero-subtitle">A fast, private music player for your library, lyrics, and sound.</p>
        <div class="hero-actions">
          <RouterLink to="/download/" class="btn btn-primary btn-lg">Get Airmedy</RouterLink><a
            href="https://github.com/misa198/airmedy" class="btn btn-outline btn-lg">View on GitHub</a>
        </div>
      </div>
      <div class="hero-mockup">
        <div class="mockup-wrapper"><img src="/screenshot-hero-devices.webp" alt="Airmedy on desktop and mobile"
            class="app-window-mockup" /></div>
      </div>
    </section>
    <section id="features" class="features container">
      <h2 class="section-title">Everything you need</h2>
      <div class="features-grid">
        <article v-for="([title, description]) in features" :key="title" class="feature-card">
          <h3>{{ title }}</h3>
          <p>{{ description }}</p>
        </article>
      </div>
    </section>
    <section id="screenshots" class="screenshots container">
      <h2 class="section-title">Designed for Focus</h2>
      <div class="screenshot-showcase">
        <article v-for="([title, description, image], index) in screenshots" :key="title"
          class="screenshot-mockup-wrapper" :class="{ reverse: index % 2 }">
          <div class="screenshot-mockup-desc">
            <h3>{{ title }}</h3>
            <p>{{ description }}</p>
          </div>
          <div class="mockup-wrapper" :class="{ 'mobile-screenshot': title === 'Airmedy Mobile' }"><img :src="image"
              :alt="title" class="app-window-mockup" /></div>
        </article>
      </div>
    </section>
  </main>
</template>
