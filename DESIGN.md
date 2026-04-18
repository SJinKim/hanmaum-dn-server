# Design System Specification: The Luminous Sanctuary

## 1. Overview & Creative North Star
**Creative North Star: The Ethereal Collective**

This design system is built to transcend the "standard" youth ministry aesthetic. Instead of loud, chaotic graphics, we are pivoting toward a "Modern Spiritual" vibe that feels intentional, premium, and breathable. We treat the interface as a digital sanctuary—a place of calm, warmth, and high-end editorial clarity.

To break the "template" look, we employ **Asymmetric Grace**. This means moving away from perfectly centered, rigid grids. We use generous whitespace (white space is not "empty," it is "sacred") and overlapping elements—such as a typography-heavy header partially obscuring a soft-focus image—to create depth and a custom, curated feel.

---

## 2. Colors & Tonal Depth

Our palette balances the warmth of human connection with the clarity of spiritual focus. We use Material Design token conventions to ensure a systematic but soulful application.

*   **Primary (`#ae2f34` / `primary_container: #ff6b6b`):** Use the Coral tones for high-energy moments and primary actions. It represents the "heart" of the community.
*   **Secondary (`#005db8` / `secondary_container: #4c96fe`):** Faith Blue provides a grounding, trustworthy foundation for navigation and secondary elements.
*   **Tertiary (`#705d00` / `tertiary_fixed: #ffe173`):** Holy Gold is reserved for "Aha!" moments, inspirational highlights, and achievement states.

### The "No-Line" Rule
Traditional 1px borders are strictly prohibited for sectioning. This design system defines boundaries through **Background Shifts**. To separate a content block, transition from `surface` (#f9f9f9) to `surface_container_low` (#f3f3f3). This creates a sophisticated, seamless flow that feels organic rather than mechanical.

### Surface Hierarchy & Nesting
Treat the UI as a series of stacked, fine-paper layers.
*   **Base:** `surface`
*   **Sectioning:** `surface_container_low`
*   **Interactive Cards:** `surface_container_lowest` (Pure White #ffffff) to create a subtle "pop" against the off-white background.

### The Glass & Gradient Rule
For hero sections or floating navigation, utilize **Glassmorphism**. Apply a semi-transparent `surface_container_lowest` with a `backdrop-blur` (20px-30px). Use subtle linear gradients transitioning from `primary` to `primary_container` on large CTAs to add "soul" and dimension.

---

## 3. Typography: Editorial Authority

We utilize **Plus Jakarta Sans** for its geometric clarity and approachable warmth.

*   **Display (lg/md):** Used for "Inspirational Statements." These should be set with tight letter-spacing (-0.02em) to feel like a high-end magazine masthead.
*   **Headline (sm/md):** Used for section titles. Pair these with high whitespace to let the message breathe.
*   **Body (lg/md):** Set in `on_surface_variant` (#584140) or Deep Charcoal. Our body text is never pure black; it's a warm charcoal to maintain the "Modern Spiritual" softness.
*   **Labels:** Always uppercase with a slight letter-spacing (+0.05em) when used for categories or eyebrow text to provide a sophisticated, curated look.

---

## 4. Elevation & Depth

We eschew traditional drop shadows in favor of **Tonal Layering**.

*   **The Layering Principle:** Depth is achieved by "stacking." A `surface_container_lowest` card placed on a `surface_container` background creates a natural lift.
*   **Ambient Shadows:** If a floating element (like a FAB or Modal) requires a shadow, use a "Large-Blur/Low-Opacity" formula.
    *   *Shadow:* 0px 20px 40px rgba(45, 52, 54, 0.06).
    *   *Tint:* The shadow should never be grey; it should be a deep, transparent version of your surface color.
*   **The "Ghost Border" Fallback:** If a border is required for accessibility, use `outline_variant` at **15% opacity**. It should be felt, not seen.

---

## 5. Components

### Buttons
*   **Primary:** Pill-shaped (`rounded-full`), using the `primary_container` color. Text is `on_primary_container`.
*   **Secondary:** Pill-shaped, `surface_container_lowest` background with a "Ghost Border."
*   **Interaction:** On hover, buttons should scale slightly (1.02x) rather than just changing color, emphasizing a "tactile" response.

### Cards
*   **Structure:** No dividers. Use `rounded-xl` (3rem) for large container cards and `rounded-lg` (2rem) for internal cards.
*   **Padding:** Aggressive internal padding (32px-48px) to reinforce the high-end editorial feel.

### Chips (Selection/Filter)
*   **Style:** Pill-shaped. Unselected chips should match `surface_container_high`. Selected chips transition to `secondary_container`.

### Input Fields
*   **Style:** `surface_container_low` background with a `rounded-md` (1.5rem) corner. The focus state uses a 2px `secondary` ghost border. Labels should "float" above the input using `label-md` typography.

### Additional Signature Component: "The Inspiration Blade"
A full-width, asymmetrical layout block using `surface_container_lowest` with a large `display-md` quote, utilizing a `primary_container` to `tertiary_fixed` soft gradient blur in the background corner.

---

## 6. Do's and Don'ts

### Do:
*   **Do** use asymmetrical image placements. Let images bleed off the edge of the container to create a sense of boundlessness.
*   **Do** prioritize "Reading Time" and "Whitespace." If a screen feels "full," remove an element.
*   **Do** use `soft_peach` (#FFF5E1) as a background for long-form reading sections to reduce eye strain and add warmth.

### Don't:
*   **Don't** use 100% opaque black (#000000). It breaks the "Sanctuary" atmosphere.
*   **Don't** use sharp 90-degree corners. Everything in this system must feel soft and approachable.
*   **Don't** use standard "Divider Lines." If you need to separate content, use a 48px-64px vertical gap or a subtle change in surface tone.
*   **Don't** crowd the logo. The brand needs room to "radiate."
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
