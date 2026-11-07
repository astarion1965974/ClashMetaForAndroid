# COUI visual fidelity audit (mainline)

Target component library: [ItosEO/COUI-Kit](https://github.com/ItosEO/COUI-Kit),
`io.github.itoseo:coui:0.0.1-alpha7`. The target is its real background
sampling / progressive top-bar blur / floating-glass controls on supported
devices, not a flat Compose imitation.

## Shared COUI host migration (in progress)

The Activity's normal XML Design roots are now wrapped by
`wrapLegacyCouiPage`: a single **COUI Scaffold** contains the legacy page
body and an independently composed `TopAppBar`. This applies to the
legacy Activity pages for App Routing, Profiles, New Profile, Properties,
Proxy, Providers, Files, Logs, Logcat, App/Network/Override/Meta Settings,
Mobile Network, Managed Routing, Help and error screens. Main/Settings
already use COUI Compose directly.

The original toolbar remains invisible but laid out to preserve old
scroll offsets. Action View instances move to toolbar slots rather than
reimplementing request handlers, retaining data binding and click actions.
The proxy delay-test FAB is hosted in Scaffold's `floatingActionButton` with
the library's one-surface renderer and the old `show()`/`hide()` bridge.
Legacy popup-list rows are replaced with actual COUI Item/RadioButton rows.

**Important:** the body still contains AndroidView and XML adapters; this
is a shared-shell migration, not proof of a completed full-Compose UI.
Real-device recording of AndroidView into COUI's graphics backdrop, and
material transparency on dialogs/menus, are not proven by JVM tests.
Visual acceptance remains a release blocker.

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

## Proxy node selection layout fix (2026-11-07)

- COUI proxy-node grid now renders equal-height 96dp cards with consistent 12dp
  gutters instead of reusing vertically padded full-width preference rows.
- Long node names are capped at two lines, with a single-line proxy
  protocol/delay summary.
- The active selector has an accent border, softly tinted card, check-circle
  indicator, and selected accessibility semantics. A successful proxy selection
  immediately updates Compose snapshot state, including linked group labels.
- Native, service, app and Compose/Robolectric tests passed on mainline build
  42363945397 (source commit `88a0687`). ColorOS 17 on-device visual review
  is still pending; this is a release of tested code, not a claim of device
  visual certification.

## Network settings startup crash guard (2026-11-07)

- When VPN is running, opening Network settings no longer creates an indefinite
  COUI notice overlay in the design constructor before the Activity installs its
  content. The "settings unavailable while running" message is displayed as
  native COUI group footer text instead.
- The network settings rows, enabled/disabled rules and navigation actions are
  unchanged.
- Regression coverage renders the network screen while the VPN state is
  active, confirms no premature overlay host is added, and verifies the inline
  warning exists. PR #10 passed the design, service and app JVM test workflow.
- Recheck on ColorOS 17 hardware, and collect the Android FATAL EXCEPTION
  log if the crash remains; the device-specific crash stack is not yet known.

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
