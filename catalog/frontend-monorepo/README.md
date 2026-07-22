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
| `formatTotalDuration` | `utils.ts` | Localized duration formatting; handles invalid values and includes days for long totals |

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
