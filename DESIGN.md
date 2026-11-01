# Clash Meta Live — UI design

This Android adaptation follows the Linear reference in
[VoltAgent/awesome-design-md](https://github.com/VoltAgent/awesome-design-md/blob/fcc70284932276d085f01ef9f83edde90b1258cb/design-md/linear.app/DESIGN.md)
(MIT). It uses the reference's surface hierarchy, lavender accent, restrained
borders and spacing. It uses Android system fonts and the app's own assets.

## Tokens

| Role | Light | Dark |
|---|---|---|
| Canvas | `#F6F7F9` | `#010102` |
| Surface | `#FFFFFF` | `#0F1011` |
| Raised | `#ECEEF3` | `#191A1B` |
| Border | `#E1E3E9` | `#23252A` |
| Primary text | `#1B1D22` | `#F7F8F8` |
| Secondary text | `#606672` | `#A1A6B0` |
| Accent | `#5E6AD2` | `#828FFF` |
| Selection fill | `#EBEDFC` | `#242844` |
| Connected status | `#247541` | `#77CF96` |

Light mode is an app-specific adaptation; the reference documents a dark
marketing website. Both modes follow the user's existing theme preference.
Theme attributes, rather than night-only resources, support manual theme changes.

## Components and layout

- 4dp spacing unit, 20dp page gutters, 16–20dp card padding.
- 12dp panels, 8dp buttons and inputs, 1dp borders, no decorative gradients.
- Headings 20–30sp medium; body 15sp; secondary text 13sp.
- Connection overview: explicit status, connect action, actual forwarded traffic
  and mode. Values unavailable while stopped display a dash.
- Profile and node navigation followed by direct app/rule routing entry points.
- Routing screens keep mode semantics, filtering, unsaved-change handling and
  save/reconnect behavior. Category destinations remain individually editable.
- Settings show a short explanation for each destination.
- Node selection uses a tinted surface and accent outline; latency has `ms` units.
- All new actions have at least 48dp targets. Content wraps and scrolls; no fixed
  height for body copy or primary actions. Long names truncate only in list rows.
- Application identity, rules, providers, notification behavior, serialization
  and signing remain the existing implementation.

## Maintenance

Keep palette values in `values/themes.xml` and shared styles in
`values/live_design.xml`. Use theme attributes for panels and borders. Preserve
native ripple/focus states and text scaling. Never indicate connection state
through color alone. Validate resource compilation and minified release builds.
