// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import postcss from 'postcss'
import { desktopFonts, desktopTarget } from './desktopFonts'

describe('desktop font targets', () => {
  it.each(['windows', 'linux', 'darwin'] as const)('uses the %s stylesheet', target => {
    expect(desktopTarget(target, 'darwin')).toBe(target)
    expect(desktopFonts(target).stylesheet).toMatch(new RegExp(`/${target}\\.css$`))
  })

  it.each([['win32', 'windows'], ['linux', 'linux'], ['darwin', 'darwin']] as const)('defaults %s to %s', (host, target) => {
    expect(desktopTarget(undefined, host)).toBe(target)
  })

  it.each(['', 'macos', 'win32', 'Windows'])('rejects invalid target %j', target => {
    expect(() => desktopTarget(target)).toThrow('Invalid AIRMEDY_TARGET_OS')
  })

  it.each([
    ['windows', ['Microsoft YaHei UI', 'Yu Gothic UI", "Meiryo UI', 'Malgun Gothic']],
    ['linux', ['Noto Sans CJK SC", "Source Han Sans SC', 'Noto Sans CJK JP", "Source Han Sans JP', 'Noto Sans CJK KR", "Source Han Sans KR']],
  ] as const)('prioritizes CJK by locale on %s', (target, fonts) => {
    const css = postcss.parse(readFileSync(desktopFonts(target).stylesheet, 'utf8'))
    const stacks = new Map<string, string>()
    css.walkRules(rule => {
      rule.walkDecls('--desktop-font-sans', decl => { stacks.set(rule.selector, decl.value) })
    })
    expect(stacks.get(':root')).toBe('"Inter Variable", sans-serif')
    for (const [index, locale] of ['zh', 'ja', 'ko'].entries()) {
      expect(stacks.get(`:root:lang(${locale})`)).toBe(`"Inter Variable", "${fonts[index]}", sans-serif`)
    }
    expect(stacks.size).toBe(4)
  })

  it('keeps the macOS system stack without Inter', () => {
    const css = readFileSync(desktopFonts('darwin').stylesheet, 'utf8')
    expect(css).toContain('--font-sans: -apple-system, BlinkMacSystemFont, "PingFang SC"')
    expect(css).not.toMatch(/Inter|@import|@font-face/i)
  })
})
