# Airmedy public site

Vue and Vite source for the public Airmedy website. Build with `pnpm build`; deploy `dist/`.

Shared Open Graph and X/Twitter preview metadata lives in `index.html`, using `static/airmedy-social.webp` (1280 × 640). URLs target `https://airmedy.pages.dev/`; update them when changing the production domain. Metadata is served in the initial HTML and is shared by all SPA routes.

FAQ entries live in `src/content/faq/`. Add one English Markdown file per entry, prefixed with its display order (for example, `06-new-topic.md`). The filename becomes its `/faq/new-topic/` URL.
