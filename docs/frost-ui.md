# Frost UI Design System

Frost is the shared visual language of the two Android apps and the Rust daemon's local
dashboard. It is intentionally calm and Apple-like: warm neutral greys, frosted translucent
panels, hairline borders, system typography, and one restrained blue accent.

## Principles

1. **Grey first.** Colour is reserved for status, selection, and one primary action.
2. **Glass, not neon.** Translucency, soft shadows, and inner highlights replace glow,
   scan lines, and HUD chrome.
3. **Quiet type.** System sans-serif, sentence case, generous line height. Monospace only
   inside logs, MAC addresses, and IP addresses.
4. **Calm motion.** 180–320 ms fades/springs; no infinite decorative animation.
5. **Accessible by default.** AA contrast on every surface, visible focus rings, and
   reduced motion where the platform supports it.

## Colour tokens (light)

| Token | Value | Use |
| :--- | :--- | :--- |
| `bg` | `#F2F2F7` | App background |
| `bgSecondary` | `#E9E9EF` | Gradient end / sheets |
| `surfaceGlass` | `#FFFFFF` 58% | Main cards |
| `surfaceElevated` | `#FFFFFF` 76% | Nested cards / inputs |
| `surfaceSolid` | `#FFFFFF` 92% | Dialogs / navigation bar |
| `borderSubtle` | `#000000` 6% | Hairlines |
| `borderStandard` | `#000000` 10% | Card outlines |
| `borderHighlight` | `#FFFFFF` 90% | Top inner highlight |
| `textPrimary` | `#1C1C1E` | Titles / body |
| `textSecondary` | `#6E6E73` | Captions / labels |
| `textTertiary` | `#8E8E93` | Hints / disabled |
| `accent` | `#0A84FF` | Primary action, selection, links |
| `accentDim` | `#0A84FF` 12% | Selected pill / icon chip |
| `accentBorder` | `#0A84FF` 25% | Focus / selection outline |
| `success` | `#34C759` | Running / online |
| `warning` | `#FF9F0A` | Connecting / caution |
| `danger` | `#FF3B30` | Shutdown / errors |

The Android apps offer **System / Light / Dark** appearance (Settings → Appearance on Home Phone, sun/moon button on Remote Phone). Dark mode uses graphite glass: `#1C1C1E` background, `#2C2C2E` 72% cards, `#F2F2F7` text,
the same blue accent, and 12% white borders. The web dashboard follows
`prefers-color-scheme`; the Android apps offer System / Light / Dark.

## Shape & depth

- Cards: 22–26 dp radius. Tiles: 18 dp. Inputs: 14 dp. Pills/buttons: full capsule.
- Glass card recipe:
  - translucent white fill;
  - 1 dp `borderSubtle`/`borderStandard` outline;
  - 1 dp inner top highlight (white gradient, 90% → transparent);
  - ambient shadow `#14000000`, 24 dp blur, 8 dp y-offset.
- No black drop shadows, no multi-layer neon outlines.

## Typography

| Role | Size | Weight | Tracking |
| :--- | :--- | :--- | :--- |
| Large title | 34 sp | Bold | -0.8 sp |
| Title | 24 sp | Bold | -0.5 sp |
| Headline | 18 sp | SemiBold | -0.2 sp |
| Body | 16 sp | Normal | -0.1 sp |
| Callout | 15 sp | Medium | 0 sp |
| Caption | 13 sp | Normal | 0.1 sp |
| Micro | 11 sp | Medium | 0.2 sp |

Use `FontFamily.SansSerif`; do not bundle fonts. Sentence case everywhere except 11 sp
status chips.

## Components

- **GlassPanel / PrecisionSurfaceCard**: the base frosted card.
- **GlassNavBar**: floating capsule, 18 dp side margins, solid glass, hairline border.
- **GlassNavItem**: icon + 11 sp label; selected state is an accent-dim capsule.
- **MasterWakeButton**: 148 dp frosted circle, 1.5 dp white ring, accent power glyph,
  press scale 0.97.
- **SegmentedModeSelector**: iOS-style track with a white selected segment and soft shadow.
- **Power tiles**: grey glass, colored glyph (danger for shutdown, warning for reboot,
  accent for sleep/lock, secondary for hibernate), black label, 13 sp caption.
- **Confirm sheet**: white glass, 28 dp radius, title + target summary, capsule buttons;
  destructive action uses danger text, not a red slab.
- **Log card**: glass card, monospace rows, muted timestamps, status dots.

## Background

A vertical `#F2F2F7 → #E9E9EF` gradient with 2–3 very soft blurred blobs (white, cool grey
`#D9DEE8`, warm grey `#E7E3DD`) at 25–35% alpha, blurred 60–80 dp. The blobs are static.
Real backdrop blur is unavailable below Android 12, so the glass effect is simulated with
layered alpha and highlights; do not add a blur library.

## Rust dashboard

`pc_onoff --web-ui` serves the same language at `http://127.0.0.1:9878`:

- CSS variables for both light and dark schemes;
- `backdrop-filter: blur(30px)` / `-webkit-backdrop-filter`;
- system font stack (`-apple-system`, `BlinkMacSystemFont`, `"SF Pro Text"`, `"Segoe UI"`,
  `Roboto`, sans-serif);
- responsive max-width 1040 px;
- status header, daemon card, five-action grid, details card, log card, confirm sheet;
- no frameworks, no CDNs, no external fonts.
