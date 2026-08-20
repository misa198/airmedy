# Airmedy Project Mandates

## Tech Stack

| Layer | Technology |
|---|---|
| Desktop framework | Wails v3 (Go + Vue 3) |
| Monorepo | pnpm workspaces + Turbo |
| DI | Uber FX |
| Frontend state | Pinia 3 |
| i18n | vue-i18n |
| UI components | Radix Vue, TailwindCSS v4 (`@airmedy/ui`) |
| Database | SQLite (sqlx + golang-migrate) |
| Search | Bleve v2 |
| File watching | fsnotify |
| Audio (macOS) | SFBAudioEngine (cgo) |
| Audio (Win/Linux) | miniaudio (C) |
| Metadata | TagLib + FFmpeg fallback |
| Logging | log/slog + lumberjack |

---

## Mobile App (Kotlin Multiplatform)

The desktop rules below apply to the Wails/Go/Vue application. Before changing
anything under [`mobile/`](mobile/), read [`mobile/AGENTS.md`](mobile/AGENTS.md).
It defines the Android-first KMP architecture and overrides desktop-specific UI,
testing, and platform instructions for that subtree. General repository rules
(research, conventional commits, and preserving unrelated changes) still apply.

---

## 1. Performance

- Track lists/grids MUST use `vue-virtual-scroller` (56px row height, or 36px in compact/collapsed mode).
- Heavy tasks (metadata extraction, directory scan, search indexing) MUST run in goroutines with `context.Context` cancellation.
- SQLite: use transactions for bulk writes; maintain appropriate indexes.

---

## 2. UI Design System

### Glass-Morphism Rules

- Surfaces: `background: var(--bg-glass); backdrop-filter: blur(30px)`.
- Borders: `1px solid var(--border-glass)`. Never plain `border` without explicit opacity.
- Row hover: `hover:bg-foreground/[0.04]`. Never `hover:bg-accent/50`.
- Button inactive/active: opacity variation of the theme foreground (`text-foreground/40 → text-foreground/70`), not a colour switch.
- Play button: theme primary-foreground circle + theme background icon.
- Secondary/metadata text: a muted theme token such as `text-[color:var(--text-muted)]`, optionally with opacity. Never `text-muted-foreground` on dark translucent backgrounds.
- Cards: `border-radius: 12px`, hover `scale(1.02)` + border brightness, and a theme-defined shadow token.
- Icons: **Lucide Vue only**. No Phosphor or others.
- Progress bars: 4px, expands 6px on hover, theme-foreground thumb.
- Transitions: `all 0.3s cubic-bezier(0.4, 0, 0.2, 1)`. Theme color shifts: `1.5s ease-in-out`.

### TailwindCSS v4

- Tokens defined via `@theme` directive in global CSS — **not** `tailwind.config.js extend.colors`.
- CSS custom properties are the primary theming mechanism.
- New design tokens go under `@theme` only.

### Theme-Safe Colours

- Every UI colour in a component MUST resolve from a semantic theme token or CSS custom property, so it remains correct in every supported theme. Use existing utilities such as `text-foreground`, `bg-foreground/[0.04]`, and `text-[color:var(--text-muted)]`, or add a semantic token when needed.
- Do not use raw colour values in component markup or styles: no hex, `rgb`/`rgba`, named colours, or Tailwind palette utilities such as `white`, `black`, `red-*`, `zinc-*`, or `slate-*`. The only exceptions are token declarations in global theme CSS and explicitly documented runtime/dynamic artwork values.
- Do not use white- or black-based opacity overlays for interaction states. Derive overlays, borders, muted text, icons, and disabled states from the active theme tokens instead.
- After any UI colour change, verify light, dark, and black themes; add or update the relevant component test when the state can be asserted.

### Mandatory CSS Variables

| Variable | Dark value | Black (OLED) value | Purpose |
|---|---|---|---|
| `--bg-main` | `#18181B` | `#0A0A0A` | Page background |
| `--bg-glass` | `rgba(35,35,38,0.6)` | `rgba(25,25,25,0.6)` | Glass surfaces |
| `--bg-glass-elevated` | `rgba(55,55,60,0.4)` | `rgba(45,45,45,0.4)` | Elevated glass |
| --border-glass | `rgba(255,255,255,0.1)` | same | Glass borders |
| `--primary` | `#E11D48` | same | Primary action |
| `--dynamic-primary` | extracted vibrant | same | Per-track, runtime |
| `--dynamic-surface` | extracted dominant 10–20% opacity | same | Per-track, runtime |
| `--dynamic-glow` | extracted vibrant 40% opacity | same | Per-track, runtime |

Themes: `system`, `light`, `dark` (gray), `black` (pure black for OLED). `black` applies `.dark` + `.black` CSS classes; `.black` only overrides bg vars.

### Layout Constraints

- Sidebar: `240px` width, `30px` blur.
- Player bar: `80px` height, `30px` blur, `1px solid var(--border-glass)` top border, fixed bottom.

### Typography Scale

- H1 Hero: 32px Bold, tracking -0.02em
- H2 Section: 20px Semibold
- Body: 14px Medium
- Metadata: 12px Regular, 60% opacity

### UI Primitive Rules

- Sliders/progress: `import { Slider } from '@airmedy/ui'` — never `<input type="range">`.
- Text inputs: `import { Input } from '@airmedy/ui'` — never raw `<input type="text">`.
- New shared primitives (no app-domain knowledge) → `packages/ui/src/`, export from `packages/ui/src/index.ts`.
- App-specific components (use stores/bindings/router) → `frontend/src/components/`.

### UI Implementation Checklist

- [ ] Glass blur or subtle border for depth?
- [ ] `--dynamic-*` colors verified for WCAG contrast?
- [ ] All interactions smooth — no abrupt state changes?
- [ ] Icons are Lucide Vue only?
- [ ] Cards scale 2% on hover with brighter border?
- [ ] New tokens via `@theme` directive, not config `extend`?

### Package Manager

**pnpm** only. Never npm or yarn.

---

## 2.1 Internationalization

- **12 locales:** `de en es fr it ja ko pt ru th vi zh` — all in `frontend/src/locales/`.
- Never hardcode strings. Use `$t()` in templates, `t()` from `useI18n()` in scripts.
- Never write `t('key', 'Fallback')`. Keys must exist in locale files.
- When adding a key: add to **all 12 files simultaneously** (English value + translated/placeholder in other 11), then use in component.

---

## 2.5 Frontend Component Architecture

| Type | Location | Rules |
|---|---|---|
| UI Primitives | `packages/ui/src/` (`@airmedy/ui`) | No `stores/`, `bindings/`, or `router` imports. Stateless. Shared across workspaces. |
| Feature Components | `frontend/src/components/` | May import stores/bindings. Reused across views. |
| Views/Pages | `frontend/src/views/` | Orchestrates feature components, handles page fetching. |

- Extract to composable/component when pattern appears in 3+ places.
- Prefer `<slot />` over excessive props.
- Split components >300 lines.
- Shared logic → `lib/utils.ts` or `composables/`.
- All components: `<script setup lang="ts">`, typed `defineProps<{}>()` and `defineEmits<{}>()`.
- Every non-trivial feature component needs a `.spec.ts` (Vitest + `@vue/test-utils`).

---

## 2.6 Frontend Memory Management

### Wails Events (`Events.On`)

- `Events.On()` returns `() => void` — **always store the off-function**.
- Pinia stores with `init()`: collect all off-fns in an array, expose `dispose()`, and guard `init()` against double-call with an `_initialized` flag.
- Pinia stores without `init()` (top-level `Events.On`): store off-fn as a `const`, expose `dispose()`.

```ts
// store pattern
let _offFns: (() => void)[] = []
let _initialized = false

function init() {
  if (_initialized) return
  _initialized = true
  _offFns = [
    Events.On('some:event', handler),
  ]
}

function dispose() {
  _offFns.forEach(off => off())
  _offFns = []
  _initialized = false
}
```

### Component Cleanup (`onUnmounted`)

Every component that registers any of the following **must** clean it up in `onUnmounted`:

| Resource | Cleanup |
|---|---|
| `Events.On()` | call the returned off-fn |
| `setInterval` / `setTimeout` | `clearInterval` / `clearTimeout` |
| `requestAnimationFrame` loop | `cancelAnimationFrame(rafId)` |
| `ResizeObserver` / `IntersectionObserver` / `MutationObserver` | `.disconnect()` |
| `addEventListener` on `window` / `document` / `MediaQueryList` | `removeEventListener` with the **same function reference** (store it) |
| WebGL / Canvas context | `loseContext()` via `WEBGL_lose_context` extension |
| `URL.createObjectURL` | `URL.revokeObjectURL` |

### MediaQueryList in Stores

Store the listener reference so it can be removed:

```ts
const _mq = window.matchMedia('(prefers-color-scheme: dark)')
const _handler = () => { /* ... */ }
_mq.addEventListener('change', _handler)

function dispose() {
  _mq.removeEventListener('change', _handler)
}
```

---

## 3. Go Architecture

### Hexagonal / Ports & Adapters

Dependency direction (strict): `infra → app → domain`

- **`internal/domain`** — entities + port interfaces. Zero imports from `app` or `infra`.
- **`internal/app`** — services/use cases. Zero imports from `infra`.
- **`internal/infra`** — adapter implementations:
  - `sqlite/` — repositories (SQLite + sqlx + golang-migrate)
  - `bleve/` — search (Bleve v2)
  - `audio/` — `player_darwin.go` (SFBAudioEngine), `player_miniaudio.go` (Win/Linux)
  - `metadata/` — TagLib + FFmpeg fallback
  - `artwork/` — disk cache, resize, palette extraction
  - `logging/` — zap + lumberjack
  - `wails/` — thin Wails v3 binding wrappers (translation only, no logic)
- **Entry point:** root `main.go`. No `cmd/` directory.

### Dependency Injection (Uber FX)

- Each package declares providers via `fx.Module`.
- All wiring in `main.go` via `fx.New(...)`.
- Never instantiate infra directly in `app` or `domain`.

### Error Handling & Logging

- Errors: `fmt.Errorf("context: %w", err)`.
- Domain errors defined in `domain`, translated to user messages in `infra/wails`.
- Logging: `*slog.Logger` (FX-injected). Never `log.Print*` or `fmt.Print*` in production. Use `slog.SetDefault` is called at startup so package-level `slog.*` calls are safe after FX starts.

### Go Implementation Checklist

- [ ] Business logic in `domain` or `app`, not `infra`?
- [ ] `infra` implements a defined Port interface?
- [ ] FX wires everything in `main.go`?
- [ ] Wails bindings translation-only?
- [ ] Goroutines accept `context.Context`?
- [ ] `*slog.Logger` for all logging (injected) or `slog.*` package-level calls after FX starts?

---

## 4. Git & Commits

Conventional Commits: `type(scope): description`

- Types: `feat fix chore docs style refactor perf test`
- Scopes: `core app infra domain ui meta`
- Install hooks: `task setup:hooks` (enforces `commit-msg`).

---

## 5. Data Integrity

- Schema changes MUST use golang-migrate. No destructive migrations without a down path.
- Music files are **read-only** unless user explicitly triggers Save Metadata.
- Propagate meaningful errors from Go to frontend via Wails.

---

## 6. Testing

- All bug fixes and features MUST have unit tests (Go) or component tests (Vue).
- Run `task verify` before declaring a task complete.
- Task is done only when automated tests pass AND UI/UX manually verified.

---

## 7. macOS Integration

- Now Playing widget + media keys must be kept in sync with playback state.
- "Close to tray" — music continues after main window closes.

---

## 8. Catalog Maintenance

`catalog/` contains living technical documentation. **Read before touch. Update after change.**

### Read Before Touch

| Feature area | Catalog |
|---|---|
| Library scan, folder watch, file import | `catalog/library/README.md` |
| Audio playback, queue, player state | `catalog/player/README.md` |
| Tag extraction, normalization, metadata write | `catalog/metadata/README.md` |
| Bleve search index, query | `catalog/search/README.md` |
| Artwork cache, palette, asset handler | `catalog/artwork/README.md` |
| EQ profiles, bands, presets | `catalog/equalizer/README.md` |
| Background audio analysis (loudness/spectral/tempo pipeline) | `catalog/analysis/README.md` |
| Volume normalization (preamp gain, target LUFS) | `catalog/normalization/README.md` |
| Lyrics fetch, LRC parse, sync display | `catalog/lyrics/README.md` |
| Playlist CRUD, track order, artwork | `catalog/playlists/README.md` |
| App settings, theme, language | `catalog/settings/README.md` |
| SQLite schema, migrations, repositories | `catalog/database/README.md` |
| Vue components, stores, composables, routing | `catalog/ui/README.md` |
| pnpm monorepo, @airmedy/ui, @airmedy/utils | `catalog/frontend-monorepo/README.md` |
| Overall architecture, DI, Wails IPC | `catalog/architecture/README.md` |
| Remote control server, WS protocol, auth, remote SPA | `catalog/remote/README.md` |

### Update After Change

Update the relevant catalog entry in the **same task** when any of these change:
interfaces/structs/methods, DB schema/migrations, Wails-exposed methods/events, algorithms, stores/composables/routes/components, infra adapters/FX modules.

**A task is not complete if the catalog is left stale.**

### Writing Catalogs

Catalogs are operational architecture documents, written in English. They must
let the next engineer change a feature safely without rediscovering its design
from source.

- Lead with ownership and boundaries: which layer/component owns state, side
  effects, persistence, and platform integration.
- Record the contracts that constrain future changes: public commands and data,
  data flow, invariants, lifecycle/cleanup rules, failure handling, and the
  relevant test or verification location.
- Use a compact diagram or table only when it makes a relationship clearer.
  Prefer the smallest useful map over a comprehensive file inventory.
- Document behavior that is intentional, surprising, or easy to regress; point
  to the source of truth rather than copying implementation details.
- Do not turn catalogs into changelogs, exhaustive component/file lists, or
  pixel-level animation narration. Delete stale detail instead of appending a
  correction below it.
- Keep claims source-backed. When a contract changes, update the catalog in the
  same diff as the code and its test.

---

## Implementation Workflow

1. **Research** — read relevant catalog entries, find existing patterns, reproduce bugs with a test first.
2. **Implement** — surgical changes with accompanying tests.
3. **Verify** — `wails3 task verify` (tests + linters).
4. **Update catalog** — if any catalog entry is now stale.

---

## Files Reference

- `agents/INTERACTION.md` — Inquiry vs Directive protocol.
- `agents/WAILS_V3.md` — Wails v3 alpha-specific patterns.
- `catalog/` — Living feature documentation (section 8).

## Command reference

- `pnpm` for frontend task
- `wails3` for backend task
