---
name: MALLA-SOS Emergency Safe System
colors:
  surface: '#f7f9ff'
  surface-dim: '#d7dae0'
  surface-bright: '#f7f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f1f4fa'
  surface-container: '#ebeef4'
  surface-container-high: '#e5e8ee'
  surface-container-highest: '#dfe3e8'
  on-surface: '#181c20'
  on-surface-variant: '#42474f'
  inverse-surface: '#2d3135'
  inverse-on-surface: '#eef1f7'
  outline: '#72777f'
  outline-variant: '#c2c7d0'
  surface-tint: '#35618d'
  primary: '#00375e'
  on-primary: '#ffffff'
  primary-container: '#1f4e79'
  on-primary-container: '#95bff1'
  inverse-primary: '#a0cafc'
  secondary: '#436086'
  on-secondary: '#ffffff'
  secondary-container: '#b3d1fd'
  on-secondary-container: '#3c597f'
  tertiary: '#710008'
  on-tertiary: '#ffffff'
  tertiary-container: '#9c0010'
  on-tertiary-container: '#ffa49b'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d1e4ff'
  primary-fixed-dim: '#a0cafc'
  on-primary-fixed: '#001d35'
  on-primary-fixed-variant: '#184974'
  secondary-fixed: '#d3e3ff'
  secondary-fixed-dim: '#abc8f4'
  on-secondary-fixed: '#001c39'
  on-secondary-fixed-variant: '#2a486d'
  tertiary-fixed: '#ffdad6'
  tertiary-fixed-dim: '#ffb4ac'
  on-tertiary-fixed: '#410003'
  on-tertiary-fixed-variant: '#93000e'
  background: '#f7f9ff'
  on-background: '#181c20'
  surface-variant: '#dfe3e8'
typography:
  display-lg:
    fontFamily: Roboto Flex
    fontSize: 57px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.25px
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: 0px
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: 0px
  headline-sm:
    fontFamily: Roboto Flex
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 32px
    letterSpacing: 0px
  title-lg:
    fontFamily: Roboto Flex
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: 0px
  title-md:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: 0.15px
  title-sm:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.1px
  body-lg:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0.5px
  body-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.25px
  body-sm:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.4px
  label-lg:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-md:
    fontFamily: Roboto Flex
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-sm:
    fontFamily: Roboto Flex
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system establishes an ultra-reliable, mission-critical mobile visual identity rooted in Material 3 standards. Built specifically for extreme-stress scenarios, network-denied environments, and offline mesh communications, the interface emphasizes cognitive calm, immediate legibility, and unmistakable visual hierarchy.

The aesthetic follows systematic utility: high contrast, zero ornamental distractions, and unambiguous state feedback. Tone of voice in the Spanish UI is direct, imperative, and reassuring. Visual drama is strictly forbidden across standard informational flows to maintain emotional equilibrium during crises; dramatic visual weight is exclusively reserved for the primary life-safety trigger.

## Colors

The color palette enforces absolute semiotic discipline to eliminate cognitive confusion during critical incidents.

- **Primary (`#1F4E79`) & On-Primary (`#FFFFFF`)**: Anchors top app bars, primary functional navigation, active tabs, and positive submission buttons. Communicates institutional authority, stability, and operational readiness.
- **Surfaces (`#FAFAFC` Base, `#EEF1F5` Variant)**: Crisp, high-luminance architectural planes providing continuous contrast ratios exceeding WCAG 2.1 AA standards against text and structural elements.
- **Critical SOS Red (`#C62828`)**: Strictly fenced off from general UI states. Never used for general validation errors, destructive dismissals, or decorative highlights. Exclusively applied to the core SOS broadcast control, active broadcast beacons, and high-priority distress status indicators.
- **Operational Status Tokens**:
  - **Pendiente (Pending)**: Dark Amber (`#8A5300`) with matching container tones (`#FFF0D4`). Signals messages awaiting transmission or routing without inducing panic.
  - **Entregado (Delivered)**: Forest Green (`#2E7D32`) with crisp check verification indicators.
  - **En Tránsito / Portado (Carried)**: Steel Mesh Blue (`#3D5A80`). Indicates node-to-node physical or relay transit across the local mesh network.
  - **Inactivo / Expirado (Neutral)**: Functional Slate Gray (`#5F6368`).
- **System Warning & Non-SOS Error Policy**: System failures, disconnected radios, and input validation errors must strictly use Dark Amber (`#8A5300`) or Neutral Slate (`#5F6368`), never Red.

## Typography

The typography leverages the structural mechanical clarity of Roboto Flex to maintain complete character distinction under low screen backlights, scratched displays, or direct glare.

- **Legibility Rules**: All informational copy uses generous line heights (minimum 1.4x font size) to prevent reading errors during physical movement or severe stress.
- **Spanish Character Formatting**: Special attention is paid to uppercase accented letters (`Á`, `É`, `Í`, `Ó`, `Ú`, `Ñ`) and inverted punctuation marks (`¡`, `¿`), ensuring accent geometry never collides with adjacent layout lines.
- **Labels and Metrics**: Fixed tabular numbers are enforced for geographic coordinates, timestamp offsets, signal hops, and battery percentages.

## Layout & Spacing

The layout is optimized for single-handed mobile navigation on standard 4-column Android layouts (360dp to 412dp screen widths) using strict 8dp baseline grid increments with 4dp fractional adjustments.

- **Margins & Gutters**: Outer canvas margin is strictly 16dp (`margin: 1rem`) to maximize readable area on small displays, paired with 16dp gutters (`gutter: 1rem`) between structural cards.
- **Ergonomics & Thumb Zones**: Critical activation zones (such as sending messages and triggering the emergency broadcast) are anchored to the bottom 40% of the viewport. Administrative settings, network diagnostics, and passive logs occupy upper screen areas.
- **Strict Touch Targets**:
  - General controls, chips, navigation tabs, and message cells: Minimum dimension of 48x48dp.
  - Core SOS activation target: Explicit minimum bounded target of 120x120dp to permit error-free engagement under trembling hands, visual impairment, or gloved use.

## Elevation & Depth

This system avoids heavy, atmospheric shadows to retain battery efficiency and maximize daytime sunlight readability. Elevation is expressed primarily through tonal surface tiers, complemented by tight Material 3 ambient shadows:

- **Level 0 (Flat)**: Background plane (`#FAFAFC`), non-elevated lists, and static data reads.
- **Level 1 (Card & Containers)**: `#EEF1F5` surface variant or pure white cards over `#FAFAFC` base, supported by an ultra-subtle border stroke (`1px solid #DCE2EA`) or `box-shadow: 0px 1px 2px rgba(31, 78, 121, 0.08)`.
- **Level 2 (Active Sheets & Mesh Cards)**: `box-shadow: 0px 2px 6px rgba(31, 78, 121, 0.12)`.
- **Level 3 (SOS Floating Target)**: Heavy dual-depth accent with emergency presence: `box-shadow: 0px 4px 12px rgba(198, 40, 40, 0.35)`. Provides instant depth cues that pop out from monochrome informational cards.

## Shapes

The geometric system utilizes clean, predictable roundedness (Level 2) aligned to Android Material 3 corner specifications:

- **Standard Cards & Containers**: Explicit 12dp corner radius (`rounded-lg`), delivering soft, protective containment without losing data density.
- **Input Fields & Secondary Actions**: 8dp corner radius (`rounded-md`).
- **Status Badges & Filtering Chips**: Fully pill-shaped (`9999px`) to immediately distinguish categorical state indicators from interactive cards.
- **SOS Button**: Strict 28dp corner radius or continuous 50% circle geometry. This deliberate divergence in corner curvature creates immediate tactile and visual recognition across the operating environment.

## Components

### SOS Button (`BotonSOS`)
- **Visuals**: Solid `#C62828` fill with prominent white typography and white alert icon. Minimum touch bounding box of 120x120dp. Centered at the base of the distress cockpit.
- **Interaction**: Long-press with visual progress track (3000ms duration) and haptic ticks to eliminate accidental triggers while permitting rapid, deliberate dispatch.

### Status Badges & Message Metadata
- **Pendiente**: `#FFF0D4` container, `#8A5300` text, paired with Google Material icon `schedule`.
- **Entregado**: `#E8F5E9` container, `#2E7D32` text, paired with `check_circle`.
- **En Tránsito**: `#E2E9F3` container, `#3D5A80` text, paired with `directions_walk`.
- **Inactivo / Fuera de Cobertura**: `#F1F3F4` container, `#5F6368` text, paired with `hourglass_disabled`.

### Message Cards (`TarjetaMensaje`)
- Flat white or `#EEF1F5` container with 12dp radius and 1px border (`#DCE2EA`).
- Internal layout includes recipient, payload snippet, hop count counter (`Saltos: N`), mesh timestamp, and the respective operational status badge anchored top-right.

### Form Inputs & Offline Text Entry
- Outlined style with 2dp border on focus. `#1F4E79` primary outline when active.
- Integrated character counter optimized for mesh byte-limits (e.g., "112/140 bytes").
- Error states strictly outlined in Amber `#8A5300`, accompanied by an explanatory message and warning icon.

### Radio & Mesh Network Bar
- Persistent top status element displaying local active peers: e.g., "Malla activa: 4 nodos cercanos".
- Utilizes neutral steel tone `#3D5A80` to indicate connection health without demanding excessive user attention.