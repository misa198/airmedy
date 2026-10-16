// @vitest-environment node
import { fileURLToPath } from 'node:url'
import { afterEach, expect, it, vi } from 'vitest'
import { build } from 'vite'

afterEach(() => { vi.unstubAllEnvs() })

it.each(['windows', 'darwin', 'linux'])('emits only the fonts and licenses for %s', async target => {
  vi.stubEnv('AIRMEDY_TARGET_OS', target)
  const result = await build({
    configFile: fileURLToPath(new URL('../vite.config.ts', import.meta.url)),
    logLevel: 'silent',
    build: {
      write: false,
      rolldownOptions: { input: fileURLToPath(new URL('../src/assets/index.css', import.meta.url)) },
    },
  })
  if (!('output' in result)) throw new Error('Expected a single build output')
  const assets = result.output.filter(asset => asset.type === 'asset')
  const fonts = assets.filter(asset => asset.fileName.endsWith('.woff2'))
  const css = assets.filter(asset => asset.fileName.endsWith('.css')).map(asset => String(asset.source)).join('\n')
  const license = assets.find(asset => asset.fileName === 'licenses/inter/OFL.txt')
  if (target === 'darwin') {
    expect(fonts).toHaveLength(0)
    expect(license).toBeUndefined()
    expect(css).not.toMatch(/Inter Variable|inter-latin|@font-face|data:font/)
    expect(css).toContain('-apple-system')
  } else {
    expect(fonts).toHaveLength(4)
    expect(license).toBeDefined()
    expect(css.match(/@font-face/g)).toHaveLength(4)
    for (const font of fonts) expect(css).toContain(font.fileName.replace('assets/', ''))
    expect(css).toContain(target === 'windows' ? 'Microsoft YaHei UI' : 'Noto Sans CJK SC')
  }
})
