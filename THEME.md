# Airmedy Visual Identity: Modern Glassmorphism

This document serves as the "Source of Truth" for the visual design and interactive language of the Airmedy music player. All UI implementation must strictly adhere to these specifications.

## 1. Design Philosophy
Airmedy's aesthetic is inspired by modern macOS and Apple Music: **depth, translucency, and vibrant clarity.**
- **Hierarchy through Elevation:** Use background blurs and subtle borders rather than heavy shadows to create depth.
- **Content First:** The UI should recede, allowing album art and typography to lead.
- **Interactive Fluidity:** Every interaction should feel soft and weighted (no abrupt state changes).
- **Apple Like:** Emulate the premium feel of Apple Music's interface, with a focus on clean lines, spacious layouts, and a harmonious color palette that adapts to the music.

## 2. Layout Analysis
The interface adheres to a clearly defined hierarchical structure, optimized for navigation on a horizontal desktop:

- **Left Sidebar:** Uses a hierarchical menu system (Navigation Drawer). Items are grouped (Apple Music, Library, Playlists) to help users quickly locate them. Width is fixed at `240px` with a heavy glass blur (`30px`) to provide a grounded feel.
- **Main Content:** Adopts a "Hero Section" layout with a large album image on the left and metadata information (album name, artist) on the right. Below is a traditional table-style tracklist, but with simplified lines for a more spacious feel.
- **Right Lyrics:** A separate control panel for lyrics, ensuring a synchronized audio-visual experience.
- **Playback Bar:** Fixed at the bottom, integrating both the progress bar and navigation buttons. Height: `80px`, Blur: `30px`, Top Border: `1px solid var(--border-glass)`.

## 3. Layout Analysis & Structure
The interface adheres to a clearly defined hierarchical 3-column structure, optimized for navigation on a horizontal desktop:

- **Left Sidebar:** Uses a hierarchical menu system (Navigation Drawer). Items are grouped (Apple Music, Library, Playlists) to help users quickly locate them. Width is fixed at `240px` with a heavy glass blur (`30px`) to provide a grounded feel.
- **Main Content (Middle Column):** Adopts a "Hero Section" layout.
    - **Top Section:** Large album cover image on the left; artist/album metadata in the middle/right.
    - **Bottom Section:** Traditional table-style tracklist stretching below, but with simplified lines for a more spacious feel.
- **Right Lyrics (Third Column):** A separate control panel for lyrics with an acrylic blur effect, ensuring a synchronized audio-visual experience.
- **Player Bar:** Fixed at the bottom as the foundational control layer.

### Accents & Semantic
- `--primary`: `#E11D48` (Rose/Red - Primary Action)
- `--primary-gradient`: `linear-gradient(135deg, #E11D48 0%, #FB7185 100%)`
- `--text-main`: `#FFFFFF` (Primary Headers)
- `--text-muted`: `#A1A1AA` (Secondary Metadata)
- `--accent-favorite`: `#EF4444` (Heart / Favorite)

## 4. Dynamic Theming (Artwork Sync)
Airmedy must dynamically adjust its color palette to match the currently playing track's album art.

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

## 5. Typography
- **Font Style:** Uses a modern Sans-serif font (Apple's San Francisco or Inter).
- **Weight:** Flexible use of Bold (for album/song titles) to Regular (for playlists).
- **Spacing:** Wide line-height spacing in the lyrics section makes it easy for users to follow along with the rhythm.
- **Scale:**
    - **H1 (Hero):** 32px, Bold, Tracking -0.02em.
    - **H2 (Album/Section):** 20px, Semibold.
    - **Body (Tracks):** 14px, Medium.
    - **Metadata (Artist/Time):** 12px, Regular, 60% Opacity.

## 6. Visual Effects
These effects create the premium feel of the interface:

- **Acrylic Blur:** The sidebar and lyrics panel use a background blur effect. This creates depth (Z-axis), making the interface feel like it has multiple layers stacked on top of each other instead of a flat surface.
- **Transparency:** Cards and control bars don't use solid colors but have a slight transparency, allowing colors from the album art to "bleed" through (color bleeding), creating visual connection across the entire screen.
- **Gradients:** In Fullscreen mode, a gradient layer transitioning from black to the album's main color is applied as a background for the lyrics, ensuring aesthetics while maintaining good readability.
- **Rounded Corners:** Large rounded corners (approximately 12-16px) for album art and buttons create a soft, friendly, and modern feel.

## 7. Components & Interactive Details
- **Buttons:** Rounded corners (`full` for action buttons, `8px` for others).
- **Progress Bars:** Ultra-thin (`4px`), expanding to `6px` on hover with a visible white thumb.
- **Icons:** Use thin-stroke, modern icon sets (e.g., Lucide or Phosphor) to maintain the high-end macOS feel.
- **Context Menus:** Custom glassmorphic menus for all list items (Right-click: Add to Playlist, Play Next, etc.).

### Cards (Albums/Artists)
- **Border Radius:** `12px`
- **Hover State:** Scale up by 2% (`scale(1.02)`), increase border brightness.
- **Shadow:** Subtle `0 10px 15px -3px rgba(0, 0, 0, 0.4)`.

## 8. Interactive Motion
- **Transitions:** `all 0.3s cubic-bezier(0.4, 0, 0.2, 1)`
- **List Hover:** Soft fade-in of background (`rgba(255, 255, 255, 0.05)`).
- **Navigation:** Slide and fade transitions between library views.

## 9. UX Summary
This interface prioritizes **"Content-first."** All decorative elements (blur, transparency) serve to highlight the most important entity: the music and the artist. The use of modern visual effects reduces the heaviness of Dark Mode, transforming the application into a digital work of art rather than a purely file management tool.
