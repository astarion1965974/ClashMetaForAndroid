# COUI visual fidelity audit (mainline)

Target component library: [ItosEO/COUI-Kit](https://github.com/ItosEO/COUI-Kit),
`io.github.itoseo:coui:0.0.1-alpha7`. The target is its real background
sampling / progressive top-bar blur / floating-glass controls on supported
devices, not a flat Compose imitation.

## Current implementation and remaining gaps

| Page / element | Current implementation | Status |
| --- | --- | --- |
| Home, main Settings | COUI `Scaffold`, `TopAppBar`, `Group`, `Item`, native `NavigationBar` on API 34+ | Uses the full scrolling backdrop as the native bar sampling source. Needs real-device visual review |
| App routing search | Native `SearchBar` on API 34+; `BasicTextField` only on older Android and Robolectric | Native capsule restored; whole routing page remains a View/Compose hybrid |
| Nodes/proxy tabs | `ChipTabRow` embedded in a legacy XML pager | Its independent backdrop is a flat surface, **not** the real content behind the toolbar |
| Proxy floating delay-test button | COUI `Fab` | Uses standalone renderer and foreground composition; legacy View host cannot sample the real screen content |
| Subscriptions and Providers | Legacy XML recycler + small COUI row adapters + embedded toolbar | **Not yet** a full COUI page; toolbar source is an empty standalone Compose `Scaffold` |
| App, network, mobile and managed routing settings | Mixed XML containers, embedded COUI preference rows | **Not yet** a full COUI page; gradient blur cannot cross isolated Compose/View layers without migrating the host |
| Dialogs / menus | Mixed COUI dialogs, menu popups and old View overlays | Requires page-by-page visual verification and consistent backdrop ownership |

The current `ActivityBarLayout.applyFrom` creates an isolated `Scaffold`
with `containerColor = Color.Transparent` and no body content. This does not
sample the actual XML screen and is **not** equivalent to COUI-Kit's progressive
blur. Do not claim these legacy screens are migrated merely because they render
COUI text or buttons.

## Rules for further page migrations

- Reuse COUI-Kit `CouiTheme`, `Scaffold`, top bars, `NavigationBar`,
  `Group`/`Item`, `SearchBar`, and `Fab` rather than replacing them
  with hand-painted flat Material/Compose approximations.
- The `CouiBackdrop` source must capture real opaque page content; a local
  transparent or uniform-color spacer does **not** provide real transparency.
  Draw effects in sibling overlay layers outside the sampled source to avoid
  sampling themselves.
- For a `Column` within the COUI `Scaffold`, apply scaffold padding inside
  `couiVerticalScroll` as the library's own demo does, so scrollable content
  remains available behind the floating navigation and gradient toolbar.
- Keep shader-free fallbacks only for platforms that cannot support the
  effect (or Robolectric's fake GPU). Never turn off the native path on actual
  Android 34+ just to pass screenshot/unit tests.
- Use `CouiFloatingRenderer()` for multi-part `NavigationBar`, and
  `CouiFloatingRenderer.standalone()` for single-part `Fab` and toolbar
  buttons.
- Migrate remaining XML/Compose hybrids as whole page-host layers before
  claiming full-scene blur. Do not synthesize blur by tinting a flat rectangle.

## Manual acceptance before a new stable release

On real ColorOS/Android 17 hardware (light and dark themes):

1. Scroll Home under its translucent top bar and capsule navigation; inspect
   genuine progressive blur and moving background detail, not a flat fill.
2. Inspect App routing native search capsule, focus/clear/cancel and filters.
3. Check proxy-page tabs, navigation interactions and delay-test floating
   button; verify no black/opaque rectangle or lost touch actions.
4. Inspect Profiles, Providers, App Settings, Network Settings, Mobile
   Network, Managed Routing and menus individually. These are still subject
   to migration; mark any missing glass/blur effect as **not implemented**.
5. Check keyboard, screen rotation, narrow screens, back gestures, and
   notification/network functionality, in addition to visual effects.
6. Capture device screenshots or a short screen recording of each page and
   compare with the COUI-Kit demo/reference on the same device.

**A green Robolectric CI run is a compiler/business-regression gate, not a
device-side visual fidelity certificate.** Keep release publishing explicit
until native rendering is confirmed.
