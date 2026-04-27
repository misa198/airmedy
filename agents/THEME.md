# Airmedy Visual Identity: Modern Glassmorphism

This document serves as the "Source of Truth" for the visual design and interactive language of the Airmedy music player. All UI implementation must strictly adhere to these specifications.

## 1. Design Philosophy

Airmedy's aesthetic is inspired by modern macOS and Apple Music: **depth, translucency, and vibrant clarity.**

- **Hierarchy through Elevation:** Use background blurs and subtle borders rather than heavy shadows to create depth.
- **Content First:** The UI should recede, allowing album art and typography to lead.
- **Interactive Fluidity:** Every interaction should feel soft and weighted (no abrupt state changes).
- **Apple Like:** Emulate the premium feel of Apple Music's interface — clean lines, spacious layouts, a harmonious color palette that adapts to the music.

## 2. Layout Structure

The interface uses a 3-column layout optimized for horizontal desktop navigation:

- **Left Sidebar:** Hierarchical navigation drawer. Items grouped (Library, Playlists). Width: `240px`, blur: `30px`.
- **Main Content (Middle Column):** Hero Section layout.
  - **Top:** Large album cover on the left; artist/album metadata on the right.
  - **Bottom:** Table-style tracklist with simplified lines for spacious feel.
- **Right Lyrics (Third Column):** Acrylic blur panel for synchronized lyrics display.
- **Player Bar:** Fixed at bottom. Height: `80px`, blur: `30px`, top border: `1px solid var(--border-glass)`.

### Accent Colors

- `--primary`: `#E11D48` (Rose/Red — Primary Action)
- `--primary-gradient`: `linear-gradient(135deg, #E11D48 0%, #FB7185 100%)`
- `--text-main`: `#FFFFFF` (Primary Headers)
- `--text-muted`: `#A1A1AA` (Secondary Metadata)
- `--accent-favorite`: `#EF4444` (Heart / Favorite)

## 3. CSS Framework: TailwindCSS v4

The project uses **TailwindCSS v4**, which uses a CSS-first configuration approach:

- Theme tokens are defined using `@theme` directive in CSS, not in `tailwind.config.js`.
- CSS custom properties (`--variable`) are the primary mechanism for theming — both static and dynamic values.
- When adding new design tokens, define them under `@theme` in the global stylesheet, not as `extend.colors` in the config.

## 4. Dynamic Theming (Artwork Sync)

Airmedy dynamically adjusts its color palette to match the currently playing track's album art. Color extraction happens in `internal/infra/artwork/palette.go`.

### Implementation Logic

- **Color Extraction:** Extract `Vibrant` and `Muted` palettes from album artwork on track change.
- **Contrast Awareness:**
  - Too dark for background → lighten or desaturate.
  - For text/buttons → verify WCAG contrast ratios. Fall back to white/black if the extracted color fails.
- **Transition:** `1.5s ease-in-out` CSS transition when updating theme variables for a smooth color wash.

### Dynamic Variable Overrides

Updated at runtime via JavaScript when the track changes:

- `--dynamic-primary`: Extracted vibrant color.
- `--dynamic-surface`: Artwork dominant color at 10–20% opacity.
- `--dynamic-glow`: Subtle drop shadow / outer glow from the artwork's core hue.

## 5. Typography

- **Font:** San Francisco (macOS) or Inter fallback.
- **Scale:**
  - **H1 (Hero):** 32px, Bold, tracking -0.02em.
  - **H2 (Album/Section):** 20px, Semibold.
  - **Body (Tracks):** 14px, Medium.
  - **Metadata (Artist/Time):** 12px, Regular, 60% opacity.
- **Lyrics:** Wide line-height for rhythm readability.

## 6. Visual Effects

- **Acrylic Blur:** Sidebar and lyrics panel use `backdrop-blur`. Creates Z-axis depth — layered, not flat.
- **Transparency:** Cards and bars use low-opacity backgrounds, allowing album art colors to bleed through.
- **Gradients:** Fullscreen lyrics background transitions from black → artwork primary color.
- **Rounded Corners:** 12–16px for album art and buttons.

## 7. Components & Interactive Details

- **Buttons:** `rounded-full` for action buttons, `8px` for others.
- **Progress Bars:** `4px` tall, expands to `6px` on hover, visible white thumb.
- **Icons:** Use **Lucide Vue** (thin-stroke, modern). Do not mix in Phosphor or other icon libraries.
- **Context Menus:** Custom glassmorphic menus on right-click (Add to Playlist, Play Next, etc.).

### Cards (Albums/Artists)

- **Border Radius:** `12px`
- **Hover:** Scale up 2% (`scale(1.02)`), increase border brightness.
- **Shadow:** `0 10px 15px -3px rgba(0, 0, 0, 0.4)`.

## 8. Interactive Motion

- **Standard transitions:** `all 0.3s cubic-bezier(0.4, 0, 0.2, 1)`
- **Theme color shifts:** `1.5s ease-in-out`
- **List hover:** Soft fade-in of background `rgba(255, 255, 255, 0.05)`.
- **Navigation:** Slide and fade between library views.

## 9. Mandatory CSS Variables

Always implement and reference these variables:

- `--bg-main`: `#0A0A0A`
- `--bg-glass`: `rgba(25, 25, 25, 0.6)`
- `--border-glass`: `rgba(255, 255, 255, 0.1)`
- `--primary`: `#E11D48`
- `--dynamic-primary`: Extracted artwork color (updated at runtime).
- `--dynamic-surface`: Derived artwork color at 10–20% opacity (updated at runtime).

## 10. Implementation Checklist

When creating or modifying components:

- [ ] **Depth:** Subtle border or glass blur to create hierarchy?
- [ ] **Contrast:** If using `--dynamic-*` colors, is WCAG contrast verified?
- [ ] **Fluidity:** Interactions weighted and soft? No abrupt state changes.
- [ ] **Icons:** Lucide Vue only (thin-stroke, modern)?
- [ ] **Hover:** Cards scale 2% and brighten border?
- [ ] **TailwindCSS v4:** New tokens defined via `@theme` directive, not config `extend`?

## 11. Layout Constraints

- **Sidebar:** `240px` width, `30px` blur.
- **Player Bar:** `80px` height, `30px` blur, `1px` top border, fixed at bottom.
- **Track Lists:** Always use `vue-virtual-scroller` for virtualized rendering.

## 12. UX Summary

Interface prioritizes **"Content-first."** All decorative elements (blur, transparency) serve to highlight the music and artist. Modern visual effects reduce the heaviness of Dark Mode, transforming the application into a digital work of art rather than a file management tool.
