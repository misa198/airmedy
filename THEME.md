# Airmedy Visual Identity: Modern Glassmorphism

This document serves as the "Source of Truth" for the visual design and interactive language of the Airmedy music player. All UI implementation must strictly adhere to these specifications.

## 1. Design Philosophy
Airmedy's aesthetic is inspired by modern macOS and Apple Music: **depth, translucency, and vibrant clarity.**
- **Hierarchy through Elevation:** Use background blurs and subtle borders rather than heavy shadows to create depth.
- **Content First:** The UI should recede, allowing album art and typography to lead.
- **Interactive Fluidity:** Every interaction should feel soft and weighted (no abrupt state changes).

## 2. Color Palette (System Variables)

### Base Surfaces
- `--bg-main`: `#0A0A0A` (Deepest base layer)
- `--bg-glass`: `rgba(25, 25, 25, 0.6)` (Primary translucent surface)
- `--bg-glass-elevated`: `rgba(45, 45, 45, 0.4)` (Secondary/Hover surfaces)
- `--border-glass`: `rgba(255, 255, 255, 0.1)` (The "silk" edge)

### Accents & Semantic
- `--primary`: `#E11D48` (Rose/Red - Primary Action)
- `--primary-gradient`: `linear-gradient(135deg, #E11D48 0%, #FB7185 100%)`
- `--text-main`: `#FFFFFF` (Primary Headers)
- `--text-muted`: `#A1A1AA` (Secondary Metadata)
- `--accent-favorite`: `#EF4444` (Heart / Favorite)

## 3. Dynamic Theming (Artwork Sync)
As shown in the reference image, Airmedy must dynamically adjust its color palette to match the currently playing track's album art.

### Implementation Logic
- **Color Extraction:** Extract the `Vibrant` and `Muted` palettes from the album artwork upon track change.
- **Contrast Awareness:**
    - If the extracted color is too dark for a background, it must be lightened or desaturated.
    - If used for text/buttons, verify WCAG contrast ratios. Fall back to white/black if the extracted color fails.
- **Transition:** Use a `1.5s` CSS transition (Ease-in-out) when updating theme variables to ensure a smooth "wash" of color across the UI.
### Variable Overrides
The following variables should be updated dynamically:
- `--dynamic-primary`: Extracted vibrant color.
- `--dynamic-surface`: Derived from artwork dominant color with low opacity (10-20%).
- `--dynamic-glow`: A subtle drop shadow or outer glow based on the artwork's core hue.

## 4. UI Architecture & Layout
As per the reference designs, the application follows a sophisticated 3-panel structural layout.

### A. The Global Shell
- **Sidebar (Left):** `240px` width. Fixed navigation, library, and pinned playlists. Uses a heavier glass blur (`30px`) to provide a grounded feel.
- **Top Bar (Navigation & Now Playing):**
    - **Search:** Integrated on the top left.
    - **Now Playing (Center):** A pill-shaped or rectangular compact display showing `Title - Artist` with a slim progress bar underneath.
    - **Global Controls (Right):** Volume slider, AirPlay, Lyrics toggle, and Queue toggle.
- **Main View (Center):** Dynamic content area that changes based on navigation.
- **Lyrics Panel (Right):** An optional, toggleable panel for real-time synced lyrics.

### B. Detailed View Patterns
- **Album/Playlist Detail:**
    - **Hero Section:** Large high-res cover art on the left; Title, Artist, and metadata on the right.
    - **Action Cluster:** Prominent "Play" and "Shuffle" buttons using `var(--primary)`.
    - **Tracklist:** Table-style layout. The currently playing track features an animated 3-bar EQ frequency indicator instead of a track number.
- **Search Results:** Unified layout using horizontal carousels for different categories (Artists, Albums, Songs) as described in the project spec.

### C. Immersive Modes
- **Full-Screen / Lyrics Mode:**
    - **Background:** Ultra-blurred, high-saturation expansion of the album artwork.
    - **Typography:** Large, bold, center-aligned or right-aligned lyrics with active line highlighting.
    - **Floating Info Card:** A small, highly translucent glass card at the bottom left containing track info and quality badges (e.g., "256", "Lossless").

## 5. Components & Interactive Details
- **Buttons:** Rounded corners (`full` for action buttons, `8px` for others).
- **Progress Bars:** Ultra-thin (`4px`), expanding to `6px` on hover with a visible white thumb.
- **Icons:** Use thin-stroke, modern icon sets (e.g., Lucide or Phosphor) to maintain the high-end macOS feel.
- **Context Menus:** Custom glassmorphic menus for all list items (Right-click: Add to Playlist, Play Next, etc.).

## 6. Typography
... (rest of the file)

- **Primary Font:** Inter or System Default (San Francisco on macOS).
- **Scale:**
    - **H1 (Hero):** 32px, Bold, Tracking -0.02em.
    - **H2 (Album/Section):** 20px, Semibold.
    - **Body (Tracks):** 14px, Medium.
    - **Metadata (Artist/Time):** 12px, Regular, 60% Opacity.

## 5. Component Specifications

### Cards (Albums/Artists)
- **Border Radius:** `12px`
- **Hover State:** Scale up by 2% (`scale(1.02)`), increase border brightness.
- **Shadow:** Subtle `0 10px 15px -3px rgba(0, 0, 0, 0.4)`.

### Controls & Sliders
- **Progress Bar:** Track `#27272A` (Dark Gray), Fill `var(--primary)`.
- **Thumb:** Appears only on hover; pure white, circular.
- **Buttons:** Backgroundless for secondary actions; solid blur for primary.

### Player Bar (The "Sticky")
- **Height:** `80px`
- **Blur:** Heavy (`30px`) to differentiate from the main scroll area.
- **Top Border:** `1px solid var(--border-glass)`.

## 6. Interactive Motion
- **Transitions:** `all 0.3s cubic-bezier(0.4, 0, 0.2, 1)`
- **List Hover:** Soft fade-in of background (`rgba(255, 255, 255, 0.05)`).
- **Navigation:** Slide and fade transitions between library views.

## 7. Responsive Context
- **Sidebar Width:** `240px` (Fixed on desktop, collapsible on smaller windows).
- **Grid Spacing:** `24px` gutter for Home/Recently Added.
- **Row Padding:** `12px` vertical padding for track tables.
