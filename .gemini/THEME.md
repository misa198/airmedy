# Agent Theme Mandates

This document serves as the internal instruction set for the agent to ensure strict adherence to the Airmedy visual identity.

## 1. Core Visual Principles
- **Aesthetic:** Modern Glassmorphism (macOS/Apple Music style).
- **Technique:** Use `backdrop-blur`, low-opacity backgrounds (`rgba`), and subtle borders (`1px solid var(--border-glass)`).
- **Transitions:** Always use `all 0.3s cubic-bezier(0.4, 0, 0.2, 1)` for UI changes and `1.5s ease-in-out` for theme color shifts.

## 2. Mandatory CSS Variables
Always implement and reference these variables:
- `--bg-main`: `#0A0A0A`
- `--bg-glass`: `rgba(25, 25, 25, 0.6)`
- `--border-glass`: `rgba(255, 255, 255, 0.1)`
- `--primary`: `#E11D48`
- `--dynamic-primary`: Extracted artwork color.
- `--dynamic-surface`: Derived artwork color (10-20% opacity).

## 3. Implementation Checklist
When creating or modifying components:
- [ ] **Depth:** Is there a subtle border or glass blur to create hierarchy?
- [ ] **Contrast:** If using `--dynamic-*` colors, is WCAG contrast verified?
- [ ] **Fluidity:** Are interactions weighted and soft? No abrupt state changes.
- [ ] **Icons:** Are they thin-stroke/modern (Lucide/Phosphor)?
- [ ] **Hover:** Do cards scale by 2% and brighten their borders?

## 4. Layout Constraints
- **Sidebar:** `240px` width, `30px` blur.
- **Player Bar:** `80px` height, `30px` blur, `1px` top border.
- **Grids/Lists:** Always use `vue-virtual-scroller` for tracks.

Refer to the root `THEME.md` for the full design specification.
