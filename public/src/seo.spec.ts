import { readFileSync, existsSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('public social preview', () => {
  it('exposes metadata and a local image without running the app', () => {
    const html = readFileSync('index.html', 'utf8')
    const document = new DOMParser().parseFromString(html, 'text/html')
    const meta = (key: string) => document.querySelector(`meta[property="${key}"], meta[name="${key}"]`)?.getAttribute('content')

    expect(meta('og:type')).toBe('website')
    expect(meta('og:site_name')).toBe('Airmedy')
    expect(meta('og:locale')).toBe('en_US')
    expect(meta('og:url')).toBe('https://airmedy.pages.dev/')
    expect(meta('og:title')).toBe(document.title)
    expect(meta('twitter:title')).toBe(document.title)
    expect(meta('og:description')).toBe(meta('description'))
    expect(meta('twitter:description')).toBe(meta('description'))
    expect(meta('twitter:card')).toBe('summary_large_image')
    expect(meta('robots')).toContain('max-image-preview:large')

    const image = new URL(meta('og:image')!)
    expect(image.origin).toBe(new URL(meta('og:url')!).origin)
    expect(image.protocol).toBe('https:')
    expect(meta('og:image:secure_url')).toBe(image.href)
    expect(meta('twitter:image')).toBe(image.href)
    expect(meta('og:image:type')).toBe('image/webp')
    expect(meta('og:image:width')).toBe('1280')
    expect(meta('og:image:height')).toBe('640')
    expect(meta('og:image:alt')).toBeTruthy()
    expect(meta('twitter:image:alt')).toBe(meta('og:image:alt'))
    expect(existsSync(`static${image.pathname}`)).toBe(true)
  })
})
