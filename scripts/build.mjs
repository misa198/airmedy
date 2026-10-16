import { platform } from 'node:os'
import { spawnSync } from 'node:child_process'
import { rmSync } from 'node:fs'

// Resolve the host default before Turbo hashes the build environment.
const host = platform()
const target = process.env.AIRMEDY_TARGET_OS ?? (host === 'win32' ? 'windows' : host)
if (!['windows', 'linux', 'darwin'].includes(target)) {
  throw new Error(`Invalid AIRMEDY_TARGET_OS: ${target}. Expected windows, linux or darwin.`)
}
// Turbo restores cached files over the output directory without deleting stale assets.
rmSync(new URL('../frontend/dist', import.meta.url), { recursive: true, force: true })
const result = spawnSync('pnpm', ['exec', 'turbo', 'build', ...process.argv.slice(2)], {
  stdio: 'inherit',
  shell: host === 'win32',
  env: { ...process.env, AIRMEDY_TARGET_OS: target },
})
if (result.error) throw result.error
process.exit(result.status ?? 1)
