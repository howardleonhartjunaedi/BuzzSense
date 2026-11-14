---
name: Quiet Acoustic
colors:
  surface: '#f8f9fa'
  surface-dim: '#d9dadb'
  surface-bright: '#f8f9fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f5'
  surface-container: '#edeeef'
  surface-container-high: '#e7e8e9'
  surface-container-highest: '#e1e3e4'
  on-surface: '#191c1d'
  on-surface-variant: '#45474a'
  inverse-surface: '#2e3132'
  inverse-on-surface: '#f0f1f2'
  outline: '#76777b'
  outline-variant: '#c6c6ca'
  surface-tint: '#5e5e61'
  primary: '#000000'
  on-primary: '#ffffff'
  primary-container: '#1b1c1e'
  on-primary-container: '#848386'
  inverse-primary: '#c7c6c9'
  secondary: '#3e6751'
  on-secondary: '#ffffff'
  secondary-container: '#bdeace'
  on-secondary-container: '#426b55'
  tertiary: '#000000'
  on-tertiary: '#ffffff'
  tertiary-container: '#271900'
  on-tertiary-container: '#ac7b00'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e3e2e4'
  primary-fixed-dim: '#c7c6c9'
  on-primary-fixed: '#1b1c1e'
  on-primary-fixed-variant: '#464749'
  secondary-fixed: '#c0edd1'
  secondary-fixed-dim: '#a4d1b6'
  on-secondary-fixed: '#002113'
  on-secondary-fixed-variant: '#264f3a'
  tertiary-fixed: '#ffdea8'
  tertiary-fixed-dim: '#f7bd4b'
  on-tertiary-fixed: '#271900'
  on-tertiary-fixed-variant: '#5e4200'
  background: '#f8f9fa'
  on-background: '#191c1d'
  surface-variant: '#e1e3e4'
typography:
  display-lg:
    fontFamily: Space Grotesk
    fontSize: 48px
    fontWeight: '600'
    lineHeight: 56px
    letterSpacing: -0.03em
  display-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 36px
    fontWeight: '600'
    lineHeight: 44px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Space Grotesk
    fontSize: 32px
    fontWeight: '500'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Space Grotesk
    fontSize: 26px
    fontWeight: '500'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Space Grotesk
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
  title-md:
    fontFamily: Hanken Grotesk
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  data-tabular-lg:
    fontFamily: Space Grotesk
    fontSize: 28px
    fontWeight: '500'
    lineHeight: 32px
    letterSpacing: -0.02em
  data-tabular-sm:
    fontFamily: Space Grotesk
    fontSize: 16px
    fontWeight: '500'
    lineHeight: 20px
  label-md:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Space Grotesk
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.06em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  gutter-tablet: 1.5rem
  margin: 1.25rem
  margin-tablet: 2rem
  margin-desktop: 3rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style
The design system establishes a clinical yet deeply serene atmosphere tailored for an acoustic mosquito-surveillance and health-protection application. It adapts fintech-caliber minimalism—restraint, structural clarity, and surgical data presentation—to an environmental health domain. The visual tone is silent, calm, and reassuring, stripping away unnecessary sensory noise so high-frequency acoustic monitoring data remains unambiguous.

The aesthetic philosophy is defined by pure, flat surfaces and generous whitespace. It rejects skeuomorphism, borders, gradients, and decorative elevation in favor of sheer contrast, precise typographic scale, and deliberate color zoning. Interactions evoke the feeling of high-end diagnostic hardware: immediate, quiet, and definitive.

## Colors
The palette balances stark monochromatic contrasts with natural, bio-acoustic signifiers:

- **Ink Black (`#111214`):** Anchors primary calls to action, high-priority status readouts, and core interface anchors.
- **Forest Green (`#1E4733`):** Serves as the primary biological accent and safe/cleared state marker, invoking ecological harmony and verified safety.
- **Mustard (`#E0A838`):** Reserved for focal metrics, target frequencies, active listening states, and moderate watch warnings.
- **Soft Mist Grey (`#F4F5F6`):** The foundational tone for cards, secondary containers, and grouped metrics against pure canvas backgrounds.
- **Result States:** 
  - *Safe / No Detection:* Forest Green (`#1E4733`)
  - *Elevated / Proximity Warning:* Warm Orange (`#D95A2B`)
  - *Confirmed Vector / Hazard:* Alert Red (`#C82D2D`)

Canvases rely exclusively on solid `#FFFFFF` in light mode and pure `#000000` in dark mode. Surface segregation occurs solely through value shifts against Mist Grey; outlines, keyline dividers, and multi-color gradients are strictly prohibited.

## Typography
Typographic discipline prioritizes instant legibility and acoustic data parsing. The geometric precision of Space Grotesk directs display and quantitative interfaces, while Hanken Grotesk anchors body narration and status descriptions with neutral clarity.

### Two-Tone Tabular Formatting
Acoustic frequency readouts (e.g., wingbeat signatures like `620 Hz` or `48 dB`) employ two-tone styling to eliminate visual cognitive load:
- The numeric value is rendered in Ink Black (light mode) or White (dark mode) with tabular numerals (`tnum`).
- The metric unit (`Hz`, `dB`, `kHz`) follows immediately in the same size or one step down, rendered in a secondary muted neutral tone (60% alpha).
- Labels above tabular figures must use uppercase `label-sm` with widened letter-spacing to form a clear analytical ceiling.

## Layout & Spacing
The layout model employs a strict fluid grid system built around vertical clarity and low item density. 

- **Mobile (Phone):** 4 columns, 16px gutters, and 20px outer canvas margins. Full-width cards standard.
- **Tablet / Large Handheld:** 8 columns, 24px gutters, and 32px margins. Modules split into dual analysis panes (acoustic waveform left, diagnostics right).
- **Desktop / Workstation:** 12 columns, 24px gutters, and 48px margins with a constrained max container width of 1120px to maintain clean, human-scale proportion.

Layouts rely on air rather than lines. Elements are separated through systematic leaps across the spacing tokens: `space-xl` separates distinct acoustic metric zones, while related controls bind closely with `space-sm` and `space-xs`.

## Elevation & Depth
This system features a strictly flat, zero-elevation architecture. Box shadows, dropshadows, blurred glass backdrops, and surface keylines are excluded entirely.

Depth and contextual hierarchy are produced via two techniques:
1. **Planar Tonal Contrast:** Pure canvas layers (`#FFFFFF` or `#000000`) serve as the base void. Interactive surfaces, inspection modules, and detection cards sit directly on this canvas as solid Soft Mist Grey (`#F4F5F6`) planes.
2. **Chromic Foreground Stacking:** Transient sheets and alert overlays do not blur the background. They enter as flat, solid white or dark opaque layers, cleanly occluding underlying cards with pure edges.

## Shapes
Geometry is disciplined and architectural. A base roundedness of 8px (`0.5rem`) balances clinical precision with modern ergonomic comfort. 

- Interactive controls, buttons, and input fields utilize `0.5rem` border radii.
- Grouping containers, acoustic waveform monitors, and scan result modules employ `rounded-lg` (16px / `1rem`).
- Full-screen modal sheets or navigation docks apply `rounded-xl` (24px / `1.5rem`) along top edge junctures.
- No pill shapes or fully circular badges are permitted for state tags; status tags remain square-cornered or softly bounded to preserve an analytical instrument identity.

## Components

### Buttons
- **Primary Action:** Solid Ink Black (`#111214`) fill, white text, 0.5rem radius, zero border, no shadow. Height is 48px to meet Android accessibility guidelines.
- **Secondary Action:** Mist Grey fill (`#F4F5F6`), Ink Black text. 
- **Destructive/Critical Action:** Solid Alert Red (`#C82D2D`), white text.
- **State Changes:** Visual feedback on tap is represented solely by an immediate alpha shift (90% opacity), never by simulated physical sinking or glow.

### Cards & Result Modules
- Built exclusively with flat Mist Grey backgrounds on pure white/black canvas.
- No perimeter borders.
- Padding follows strict internal intervals (`space-lg` outer, `space-md` inner).
- Result state modules switch the entire header badge or primary numeric readout color to either Forest Green (negative detection), Warm Orange (ambient activity), or Alert Red (target species confirmed).

### Two-Tone Acoustic Metric Chips
- Compact rectangular badges set on Mist Grey or white.
- Composed of uppercase Space Grotesk typography showing the parameter name in 50% opacity, followed by the active value in saturated Mustard (`#E0A838`) or Forest Green (`#1E4733`).

### Lists & Activity Rows
- Items stack with vertical rhythm separated by `space-sm` gaps, never using hair-thin hairline borders or horizontal divider rules.
- Selection states highlight the target row with a flat Mist Grey background block.

### Inputs & Selection Controls
- **Fields:** Flat Mist Grey container background, transparent fill, Ink Black text, placeholder in 40% ink tone. Focus state changes the background to an ultra-light tint without showing a border outline.
- **Checkboxes & Radios:** Monolithic solid fills. Unchecked states sit as flat subdued squares; checked states fill with Ink Black or Forest Green with white glyphs.

### Frequency / Waveform Visualizer
- Flat, vector-drawn frequency bars or continuous paths rendered in Forest Green or Mustard.
- Baseline grid markers use transparent neutral values without visible horizontal ticks or gradient fills below the curve.