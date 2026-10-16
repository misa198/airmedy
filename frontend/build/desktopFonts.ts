import { platform } from 'node:os'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'

export function desktopTarget(target = process.env.AIRMEDY_TARGET_OS, host = platform()) {
  const os = target ?? (host === 'win32' ? 'windows' : host)
  if (os !== 'windows' && os !== 'linux' && os !== 'darwin') {
    throw new Error(`Invalid AIRMEDY_TARGET_OS: ${os}. Expected windows, linux or darwin.`)
  }
  return os
}

export function desktopFonts(target: ReturnType<typeof desktopTarget>) {
  const directory = new URL('../src/assets/fonts/', import.meta.url)
  const license: Plugin = {
    name: 'desktop-font-license',
    generateBundle() {
      if (target === 'darwin') return
      for (const name of ['OFL.txt', 'SOURCE.md']) {
        this.emitFile({ type: 'asset', fileName: `licenses/inter/${name}`, source: readFileSync(new URL(`inter/${name}`, directory)) })
      }
    },
  }
  return { stylesheet: fileURLToPath(new URL(`${target}.css`, directory)), license }
}
