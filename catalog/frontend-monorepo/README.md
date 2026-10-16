# Frontend Monorepo

## Summary

The frontend is part of a **pnpm + Turbo monorepo** rooted at the repo root. Shared UI primitives and utilities live in `packages/` as separate workspace packages consumed by both `frontend` and `remote`.

## Workspace Layout

```
/  (repo root)
├── package.json          # root — turbo scripts only
├── pnpm-workspace.yaml   # declares workspace members
├── turbo.json            # pipeline: build, dev, lint
│
├── frontend/             # @airmedy/frontend — main desktop Vue 3 app
├── remote/               # remote web app (browser-based remote control)
└── packages/
    ├── ui/               # @airmedy/ui — shared UI component library
    └── utils/            # @airmedy/utils — shared utilities
```

**pnpm-workspace.yaml** includes `remote`, `frontend`, and `packages/*` — any directory under `packages/` is automatically a workspace member.

## Turbo Pipeline

| Task    | Behavior                                     |
| ------- | -------------------------------------------- |
| `build` | Topological (`dependsOn: ["^build"]`). Packages build before apps that consume them. Outputs `dist/**`. |
| `dev`   | Persistent, no cache. All packages run their `dev` script simultaneously. |
| `lint`  | Parallel across all packages.                |

Run from repo root:
```bash
pnpm dev       # starts all dev servers in parallel via turbo
pnpm build     # builds packages then apps in dependency order
pnpm lint      # lints all packages
```

## `@airmedy/utils` (`packages/utils/`)

Shared utility functions. Zero Vue dependency — safe to import anywhere.

| Export       | Source         | Purpose                             |
| ------------ | -------------- | ----------------------------------- |
| `cn`         | `utils.ts`     | `clsx` + `tailwind-merge` combiner  |
| `logger`     | `logger.ts`    | Structured logging helpers          |
| `test-utils` | `test-utils.ts`| Vitest/Vue Test Utils helpers       |
| `formatTotalDuration` | `utils.ts` | Localized duration formatting; shows seconds below one minute, handles invalid values, and includes days for long totals |

**Peer deps** (not bundled): `pinia`, `vue-i18n`.

The package exports its TypeScript source directly (`"main": "./src/index.ts"`) — no build step needed because consumers resolve it via path alias at dev time.

## `@airmedy/ui` (`packages/ui/`)

Reusable UI component library built on **Radix Vue** + **Tailwind CSS**. Components follow the Shadcn pattern: unstyled primitives wrapped with project-specific Tailwind classes.

### Build

Vite library build produces:
- `dist/index.js` — ESM
- `dist/index.cjs` — CJS
- `dist/index.d.ts` — types (via `vite-plugin-dts`)

Run `pnpm --filter @airmedy/ui build` (or `pnpm dev` from root for watch mode).

### Exported Components

| Export              | File                              | Primitive             |
| ------------------- | --------------------------------- | --------------------- |
| `MarqueeText`       | `MarqueeText.vue`                 | Custom CSS animation  |
| `Modal`             | `Modal.vue`                       | Custom dialog         |
| `DetailsButton`     | `DetailsButton.vue`               | Button variant        |
| `TabSwitcher`       | `TabSwitcher.vue`                 | Custom tabs           |
| `Badge`             | `Badge.vue`                       | Theme-aware status label |
| `Checkbox`          | `checkbox/Checkbox.vue`           | Radix CheckboxRoot    |
| `Tooltip`           | `tooltip/Tooltip.vue`             | Custom CSS (group-hover popup) |
| `Input`, `index`    | `input/`                          | Native input wrapper  |
| `Slider`            | `slider/`                         | Radix SliderRoot      |
| `Switch`            | `switch/`                         | Radix SwitchRoot      |
| `Select`, `SelectContent`, `SelectItem`, `SelectValue`, … | `select/` | Radix SelectRoot family |
| `ResizablePanel`, `ResizableHandle`, `ResizablePanelGroup` | `resizable/` | vue-resizable-panels |

All exports are re-exported from `packages/ui/src/index.ts`.

`TabSwitcher` supports icon tabs and variable-width label tabs. Its label
variant tracks the active button's geometry with `ResizeObserver` so the
selection slider remains aligned after content or layout changes.

`Badge` accepts a CSS `color` value (default `var(--primary)`) and applies it
to its text and border; its background uses the same colour at low opacity.

### Dependencies

| Package                | Purpose                           |
| ---------------------- | --------------------------------- |
| `radix-vue`            | Accessible headless primitives    |
| `@lucide/vue`      | Icon set                          |
| `vue-resizable-panels` | Resizable panel layout            |
| `@airmedy/utils`       | `cn` utility for class merging    |

Peer dep: `vue ^3.5.0`.

### Adding a New Component

1. Create `packages/ui/src/<name>.vue` (or `<name>/index.ts` for multi-file)
2. Export from `packages/ui/src/index.ts`
3. Import in consumers via `import { MyComponent } from '@airmedy/ui'`

No rebuild needed in dev — the path alias resolves directly to source.

## How `frontend` Consumes the Packages

`frontend/vite.config.ts` and `frontend/tsconfig.json` define path aliases that resolve directly to package source (bypassing `dist/`):

```typescript
// vite.config.ts (resolve.alias)
'@airmedy/ui':    '../packages/ui/src'
'@airmedy/utils': '../packages/utils/src'
'@':              './src'
```

This means:
- **No build step needed** before running `frontend` in dev mode
- **Hot Module Replacement** works across package boundaries
- Changes to `packages/ui/src/` hot-reload in the running frontend instantly

In production builds, Turbo's topological order ensures `@airmedy/ui` builds before `@airmedy/frontend`.

### Desktop typography and build target

The desktop Vite build selects one font stylesheet using `AIRMEDY_TARGET_OS`
(`windows`, `linux`, or `darwin`). When unset, it uses the build host's OS;
invalid values, including an empty string, fail the build. Native and Docker
cross-build Task targets pass their destination explicitly. `common:build:frontend`
always runs before embedding desktop assets, and Vite clears `frontend/dist` so
switching targets cannot retain old fonts or licenses. The root `pnpm build`
resolves the host default before invoking Turbo; the resolved target participates
in Turbo's build cache key. It also clears desktop output before Turbo restores
cached assets, since cache restoration overlays existing files. Use that entry
point for cached monorepo builds.

Windows/Linux bundle four Inter Variable WOFF2 subsets (about 159 KiB): Latin,
Latin Extended, Vietnamese and Cyrillic, normal style, weights 100–900.
`unicode-range` and `font-display: swap` allow selective loading. Version, source,
checksums and SIL OFL 1.1 live in `frontend/src/assets/fonts/inter/`; builds emit
the license and provenance under `dist/licenses/inter/`. Fonts live outside
`public/`. Darwin retains the existing system font stack and includes neither
Inter declarations, font data nor its license. Remote and mobile typography are
independent of this policy.

The selected stylesheet owns the `@theme` sans token. Windows/Linux CJK fallback
follows the document's UI locale:

| Locale | Windows | Linux |
| --- | --- | --- |
| `zh` | Microsoft YaHei UI | Noto Sans CJK SC, Source Han Sans SC |
| `ja` | Yu Gothic UI, Meiryo UI | Noto Sans CJK JP, Source Han Sans JP |
| `ko` | Malgun Gothic | Noto Sans CJK KR, Source Han Sans KR |
| Others | System `sans-serif` fallback | System `sans-serif` fallback |

Every stack ends in `sans-serif`. No CJK font or italic face is bundled.
Missing CJK glyphs depend on installed system fonts; small differences between
machines are expected. Metadata and lyrics inherit the same stack without
inferring a track's language. `locales/index.ts` synchronizes `<html lang>` with
vue-i18n immediately, and the app store receives language changes from other
desktop windows. This covers both the main window and mini player. GPU lyrics
read computed DOM fonts, wait for font readiness, and invalidate their measured
textures after font loading or a document language change.

Tests: `frontend/build/desktopFonts*.spec.ts`, `frontend/src/locales/index.spec.ts`,
`GeneralSettings.spec.ts` and `useGpuLyrics.spec.ts`. Release checks must also
build Windows → Darwin → Linux in one workspace and inspect CSS, font bytes and
licenses. Verify mixed Latin/Vietnamese/CJK text at 12/14/20px and weights
400/500/600/700 in track tables, settings, lyrics and mini player, in
light/dark/black themes. Check Windows at 100%/125% scaling and Linux with and
without Noto CJK; automated tests cannot establish glyph clipping or visual
weight harmony on those native webviews.

### Importing in `frontend`

```typescript
import { Modal, Checkbox, cn } from '@airmedy/ui'
import { cn, logger } from '@airmedy/utils'
```

## Adding a New Workspace Package

1. Create `packages/<name>/` with a `package.json`:
   ```json
   {
     "name": "@airmedy/<name>",
     "version": "0.0.1",
     "private": true,
     "type": "module"
   }
   ```
2. No change needed to `pnpm-workspace.yaml` — `packages/*` glob covers it.
3. Add a `turbo.json` pipeline entry if the package needs a `build` task.
4. Run `pnpm install` from repo root to link workspace deps.

## Distinction: `@airmedy/ui` vs `frontend/src/components/`

| Location                      | What belongs there                                      |
| ----------------------------- | ------------------------------------------------------- |
| `packages/ui/src/`            | Generic primitives with no app-domain knowledge (no Pinia stores, no Wails bindings, no route awareness) |
| `frontend/src/components/`    | App-specific feature components (TrackTable, PlayerFooter, MetadataEditDialog, etc.) |

If a component needs to import from `stores/`, `bindings/`, or `router/`, it belongs in `frontend/src/components/`, not `@airmedy/ui`.
