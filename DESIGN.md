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
