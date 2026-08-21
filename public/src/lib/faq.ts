export interface FaqEntry {
  slug: string
  title: string
  body: string
  order: number
  /** Map from bare filename (e.g. "desktop-qr-code.webp") to the Vite-resolved URL */
  images: Record<string, string>
}

// Matches both flat files (01-slug.md) and folder entries (01-slug/index.md)
const filename = /(?:.*\/)?(\d+)-([a-z0-9-]+)(?:\/index)?\.md$/

export function parseFaqFiles(
  markdownFiles: Record<string, string>,
  imageFiles: Record<string, string> = {},
): FaqEntry[] {
  // Build a lookup: folder prefix → { basename → resolved URL }
  // e.g. "../content/faq/05-mobile-sync/desktop-qr-code.webp" → "desktop-qr-code.webp"
  const imagesByFolder: Record<string, Record<string, string>> = {}
  for (const [imgPath, url] of Object.entries(imageFiles)) {
    const slashIdx = imgPath.lastIndexOf('/')
    const folder = imgPath.slice(0, slashIdx)   // "../content/faq/05-mobile-sync"
    const base = imgPath.slice(slashIdx + 1)     // "desktop-qr-code.webp"
    if (!imagesByFolder[folder]) imagesByFolder[folder] = {}
    imagesByFolder[folder][base] = url
  }

  return Object.entries(markdownFiles).flatMap(([path, body]) => {
    const match = path.match(filename)
    const title = body.match(/^#\s+(.+)$/m)?.[1]
    if (!match || !title) return []

    // Determine the folder of this markdown file
    const mdFolder = path.replace(/\/index\.md$/, '').replace(/\.md$/, '')
    const images = imagesByFolder[mdFolder] ?? {}

    return [{ order: Number(match[1]), slug: match[2], title, body, images }]
  }).sort((a, b) => a.order - b.order)
}

const markdownFiles = import.meta.glob(
  ['../content/faq/*.md', '../content/faq/*/index.md'],
  { eager: true, query: '?raw', import: 'default' },
) as Record<string, string>

// Import all images co-located with folder-based FAQ entries so Vite can
// process them (hash, copy to dist) and give us resolved URLs at build time.
const imageFiles = import.meta.glob(
  '../content/faq/**/*.{webp,png,jpg,jpeg,gif,svg}',
  { eager: true, import: 'default' },
) as Record<string, string>

export const faqs = parseFaqFiles(markdownFiles, imageFiles)
