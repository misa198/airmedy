import { describe, expect, it } from 'vitest'
import { parseFaqFiles } from './faq'

describe('parseFaqFiles', () => {
  it('orders prefixed Markdown files and derives their slugs', () => {
    expect(parseFaqFiles({
      '../content/faq/02-second.md': '# Second\n',
      '../content/faq/01-first.md': '# First\n',
    })).toMatchObject([
      { order: 1, slug: 'first', title: 'First' },
      { order: 2, slug: 'second', title: 'Second' },
    ])
  })
})
