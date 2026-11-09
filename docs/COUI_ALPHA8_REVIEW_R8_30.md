# R8.30 ColorOS 17 / COUI-Kit alpha8 all-page review

Baseline: application `v2.11.36-coui-r8.29`, COUI-Kit tag `v0.0.1-alpha8`.
Review authority: alpha8 `DemoApp`, `DemoSettingsPage`, `DemoCatalog`,
`DemoPreferenceRows`, `DemoSettingsSamples`, `DemoChipSamples`,
`DemoChromeDetail`, `DemoSamples` and source `CouiPageComponents`, `Scaffold`, `Menu`.

## Decisions applied globally

| Surface | Demo contract | App implementation |
| --- | --- | --- |
| Main tabs | Stable content swap; native NavigationBar owns material transition | Removed custom per-page 0.94/1.045 scale, fade and 12dp blur; maintained separate scroll/title state |
| Settings rows | Group + Item with 16dp card, 16dp row padding, 24dp icon and 16dp spacing | Shared `SettingDivider(withIcon)`: 56dp icon-bearing or 16dp plain, 16dp end inset; explicit mixed rows |
| Choice/switch controls | 12x24 arrow with 4dp leading gap; Switch with 16dp gap and native accent | Shared `NativeCouiPreferences` updated; safe exception recovery on choice writes |
| Large title | Native alpha8 large-title ramp and glass behavior | Shared title slot inherits `LocalCouiTextStyle`; manual expanded/collapsed font override removed |
| Secondary/tertiary pages | One COUI Scaffold, nested scroll, real inset, group bottom margin | Shared `ColorOsSubPage`, `ColorOsLazySubPage`, `ColorOsFormPage` and lazy form corrected |
| List/backdrop | Full viewport records scrolling content; content insets go inside Lazy items | Corrected native LazyColumn and proxy grid, including tab measurement and source order |
| Proxy groups | COUI ChipTabRow with recorded backdrop and anchored row | Per-group saveable LazyGridState, measured offset and non-scrolling overlay |
| Overflow | Anchored COUI Menu with native opening/dismiss, not centered Dialog | Proxy, application routing bulk, profile and file row menus share `CouiAnchoredMenu` |
| Dialogs | True modal confirmation/input uses COUI Dialog/InputDialog | Existing modal selection and edit dialogs retained |
| Android scope | Target ColorOS 17 | No old-Android migration or backward compatibility layers added |

## Pages covered by shared host and direct implementations

- Main screen (connection, subscription, traffic, DNS, persistent navigation)
- Main profile tab, standalone profile list and editor
- Main settings, App settings, Network settings, Mobile network, managed routing
- Configuration override, Meta features, help
- Proxy nodes and strategies, proxy provider list
- App access controls, selection and search
- File browser and native editor, log lists and log viewer
- New profile, recovery/error views and native dialogs

## Test gates

1. UI unit tests including exact divider/title coordinate assertions for icon/non-icon rows.
2. Primary tab actions, profile selection, managed routing save, app selection, layout and back-navigation tests.
3. Android Gradle design/service/app JVM tests and native routing/mobile tests.
4. Mainline minified ARM64 build, signing identity verification and GitHub release.
5. Real-device ColorOS 17 visual sign-off remains separate from headless CI: glass source,
   GPU shader, OS notification expansion and gesture continuity cannot be proven by Robolectric.

## Review note

The old `docs/COUI_VISUAL_AUDIT.md` contains historical pre-migration observations; it
is not the current state of the app. This document records the R8.30 source contracts
without treating green JVM tests as on-device proof.
