<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const version = ref('')
const mobileVersion = ref('')
const releases = computed(() => `https://github.com/misa198/airmedy/releases/download/v${version.value}/Airmedy_${version.value}_`)
const mobileRelease = computed(() => `https://github.com/misa198/airmedy/releases/download/mobile-v${mobileVersion.value}/Airmedy-mobile-mobile-v${mobileVersion.value}.apk`)
type Platform = { name: string, icon: string, files: [string, string][], mobile?: true }

const platforms: Platform[] = [
  { name: 'Windows', icon: '/windows.svg', files: [['Windows (x64)', 'windows-amd64_installer.zip'], ['Windows (ARM64)', 'windows-arm64_installer.zip']] },
  { name: 'Linux', icon: '/linux.svg', files: [['Debian / Ubuntu (x64)', 'linux-amd64.deb'], ['Debian / Ubuntu (ARM64)', 'linux-arm64.deb'], ['Fedora / RHEL (x64)', 'linux-amd64.rpm'], ['Fedora / RHEL (ARM64)', 'linux-arm64.rpm'], ['Arch (x64)', 'linux-amd64.pkg.tar.zst'], ['Arch (ARM64)', 'linux-arm64.pkg.tar.zst']] },
  { name: 'macOS', icon: '/apple.svg', files: [['Apple Silicon', 'darwin-arm64.dmg'], ['Intel Chip', 'darwin-amd64.dmg']] },
  { name: 'Android', icon: '/android.svg', files: [['Android (ARM64)', '']], mobile: true },
]

onMounted(async () => {
  try {
    const config = await fetch('/config.json').then(response => response.json())
    version.value = config.downloadVersion
    mobileVersion.value = config.mobileDownloadVersion
  } catch {
    version.value = ''
  }
})
</script>

<template>
  <main>
    <section class="download container">
      <div class="download-card">
        <h2>Ready to listen?</h2>
        <p>Download Airmedy for your platform and start your journey.</p>
        <div class="download-options">
          <div v-for="platform in platforms" :key="platform.name" class="download-group">
            <span
              class="platform-icon"
              role="img"
              :aria-label="`${platform.name} icon`"
              :style="{ '--platform-icon': `url(${platform.icon})` }"
            />
            <span class="platform-label">{{ platform.name }}</span>
            <div v-if="platform.files.length" class="dropdown">
              <button class="btn btn-outline dropdown-trigger">Download for {{ platform.name }} <span class="chevron">▾</span></button>
              <div class="dropdown-content"><div class="dropdown-content-inner glass">
                <a v-for="([label, file]) in platform.files" :key="label" :href="platform.mobile ? (mobileVersion ? mobileRelease : undefined) : (version ? `${releases}${file}` : undefined)">{{ label }}</a>
              </div></div>
            </div>
            <span v-else class="btn btn-outline disabled">Coming soon</span>
          </div>
        </div>
        <p class="download-license">Released under the <a href="https://www.gnu.org/licenses/gpl-3.0.html">GPL-3.0 License</a></p>
      </div>
    </section>
  </main>
</template>
