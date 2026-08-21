export interface FaqEntry {
  slug: string
  title: string
  body: string
  order: number
}

const filename = /(?:.*\/)?(\d+)-([a-z0-9-]+)\.md$/

export function parseFaqFiles(files: Record<string, string>): FaqEntry[] {
  return Object.entries(files).flatMap(([path, body]) => {
    const match = path.match(filename)
    const title = body.match(/^#\s+(.+)$/m)?.[1]
    if (!match || !title) return []
    return [{ order: Number(match[1]), slug: match[2], title, body }]
  }).sort((a, b) => a.order - b.order)
}

const files = import.meta.glob('../content/faq/*.md', { eager: true, query: '?raw', import: 'default' }) as Record<string, string>

export const faqs = parseFaqFiles(files)
