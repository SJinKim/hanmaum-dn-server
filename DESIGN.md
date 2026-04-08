# Design System: DN App

## 1. Visual Theme & Atmosphere

The **DN App** is a "Digital Church Management" designed for the mobile-first generation. It rejects the cold, industrial feel of traditional productivity apps in favor of a **"Warm Digital Paper"** aesthetic. The atmosphere is bright, airy, and hyper-legible, prioritizing spiritual reflection and community connection over complex UI density.

The visual signature is the **"Divine Glow"**—a subtle use of iridescent gradients (Holy Gold to Soft Peach) and soft atmospheric shadows that make content feel like it's floating on light. Every interaction is designed to be gentle, using high border-radii (24px+) to create a "soft-to-the-touch" interface that communicates safety, community, and peace.

**Key Characteristics:**
- **Mobile-First Sanctuary**: Optimized for one-handed use with bottom-heavy navigation and large touch targets.
- **Pretendard Typography**: A modern, high-readability sans-serif optimized for both Korean and English mobile environments.
- **Warm Canvas**: Base background is `#FAFAFA` (Sanctuary White) to provide a cozy, focused environment.
- **Vibrant Accents**: "Youthful Coral" for passion/actions and "Faith Blue" for trust/meditation.
- **Elevation through Light**: Surfaces are defined by soft ambient shadows (`0px 8px 24px rgba(0,0,0,0.06)`) rather than harsh borders.
- **8px Base Grid**: Consistent spacing system with 4px micro-adjustments for mobile precision.

## 2. Color Palette & Roles

### Primary (Brand Identity)
- **Youthful Coral** (`#FF6B6B`): Primary Action. Energy, Passion, Community. Used for main buttons and active states.
- **Faith Blue** (`#4D96FF`): Spiritual Depth. Peace, Truth, Study. Used for meditation features and navigation.
- **Growth Green** (`#6BCB77`): Progress. Sharing, New Life, Success states.

### Secondary & Accent
- **Holy Gold** (`#FFD93D`): Inspiration. Used for "Word of the Day" and special badges.
- **Soft Peach** (`#FFF5E1`): Warmth. Used as a secondary surface or highlight background.

### Surface Scale (Mobile Depth)
- **Bg Sanctuary** (`#FAFAFA`): Root page background.
- **Surface Paper** (`#FFFFFF`): Primary card and sheet surface.
- **Surface Muted** (`#F1F2F6`): Inset areas, search bars, and disabled states.

### Text & Icons
- **Deep Charcoal** (`#2D3436`): Primary text. High contrast but warmer than pure black.
- **Warm Gray** (`#636E72`): Secondary text, timestamps, and metadata.
- **Icon Muted** (`#B2BEC3`): Inactive icon states in navigation.

---

## 3. Typography Rules

### Font Family
- **Primary**: `Pretendard`, `system-ui`, `-apple-system`.
- **Display**: `Pretendard Bold`.

### Hierarchy (Mobile Optimized)

| Role | Size | Weight | Line Height | Letter Spacing | Use Case |
|------|------|--------|-------------|----------------|----------|
| **Display 1** | 32px | 700 | 1.2 | -0.5px | Hero headers, Welcome screen |
| **Heading 1** | 24px | 700 | 1.3 | -0.3px | Page titles, Section headers |
| **Heading 2** | 20px | 600 | 1.4 | -0.2px | Card titles |
| **Body Large** | 17px | 400 | 1.6 | 0 | Long-form meditation text |
| **Body Med** | 15px | 400 | 1.5 | +0.1px | Standard UI text, list items |
| **Label Bold** | 14px | 600 | 1.0 | +0.2px | Button text, Category chips |
| **Caption** | 12px | 500 | 1.4 | +0.3px | Timestamps, Metadata |

---

## 4. Component Stylings

### Buttons (Pill-Shaped)
- **Primary Action**: 54px height (Standard), `#FF6B6B` background, White text, 100px radius.
- **Secondary Action**: 48px height, `#4D96FF` background or border, 100px radius.
- **Ghost/Text**: 15px Label Bold in `#636E72` with no background.

### Cards (Community Containers)
- **Surface**: Pure White (`#FFFFFF`).
- **Radius**: 24px (Standard) or 32px (Feature cards).
- **Shadow**: `0 8px 24px rgba(45, 52, 54, 0.06)` — atmospheric and soft.
- **Border**: Optional `1px solid #EFEFEF` for definition on high-density screens.

### Bottom Navigation (Mobile)
- **Height**: 80px (including home indicator safe area).
- **Background**: White with a `20px` top radius.
- **Active State**: Icon and label shift to `Faith Blue` or `Youthful Coral`.

### Inputs
- **Search/Fields**: 52px height, `#F1F2F6` background, 16px radius, no border.
- **Focus**: Subtle `1px solid #4D96FF` border with a soft blue outer glow.

---

## 5. Layout Principles

### Spacing (8pt Grid)
- **Screen Margins**: 20px (Standard).
- **Vertical Spacing**: 24px between sections, 12px between related elements.
- **Safe Areas**: Strict adherence to iOS/Android status bar and home indicator regions.

### Scrolling & Depth
- **Sheet Pattern**: Modals appear as "Bottom Sheets" with a `32px` top radius, sliding up to cover 70-90% of the screen.
- **Sticky Headers**: Page titles should shrink and stick to the top on scroll with a `backdrop-filter: blur(10px)`.

---

## 6. Depth & Elevation

| Level | Shadow / Treatment | Component |
|-------|--------------------|-----------|
| **0 (Base)** | None (`#FAFAFA`) | Root background |
| **1 (Surface)** | `0 4px 12px rgba(0,0,0,0.03)` | Feed cards, list items |
| **2 (Float)** | `0 8px 32px rgba(0,0,0,0.08)` | Floating Action Buttons (FAB) |
| **3 (Overlay)** | `0 16px 48px rgba(0,0,0,0.12)` | Bottom sheets, Modals |

---

## 7. Interaction & Motion

### Feedback
- **Haptics**: Light haptic feedback on all primary button presses.
- **Active State**: Buttons scale down to `0.96` on press (transform: scale).
- **Transitions**: Use `300ms ease-out` for page transitions (Slide from Right).

---

## 8. Agent Prompt Guide (For Claude Code)

### Quick Color Reference
- Background: `#FAFAFA`
- Primary: `#FF6B6B` (Coral)
- Secondary: `#4D96FF` (Blue)
- Text: `#2D3436` (Charcoal)

### Prompt Examples
- "Create a **Meditation Card** using Surface White, 24px radius, and a soft 8px blur shadow. Header in 20px Bold Deep Charcoal."
- "Design a **Primary CTA** pill button with #FF6B6B background, height 54px, and white text."
- "Build a **Bottom Sheet** for 'New Reflection' with a 32px top radius and #FAFAFA header area."
- "Implement a **Feed Item** with 15px Pretendard body text, 1.6 line height, and a #636E72 timestamp."