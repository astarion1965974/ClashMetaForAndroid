# Predictive Back

## Baseline (2026-11-01)

- `main`: `50dbd407`; Live source: `a5288f7e`. App/design sources match; main additionally owns the protected weekly release control workflow. Live has the newer release version.
- minSdk 21, targetSdk 35, compileSdk 36. These remain unchanged.
- Activity was declared as 1.5.0, while app's activity-ktx requested 1.9.0. Both now use 1.9.0 explicitly, including design's compilation classpath.
- AppCompat 1.7.0 supplies modern dialog integration. Material 1.12.0 supplies built-in predictive modal bottom-sheet motion (available since Material 1.10).
- Fragment is declared as 1.5.0. There are no app Fragment/Navigation transactions or Compose screens to migrate; UI uses separate Activities and XML/Data Binding designs.
- The manifest previously had no `enableOnBackInvokedCallback` entry. Application-level opt-in now covers the normal Activities; no Activity opts out.

## Navigation ownership

| UI | Back owner and behavior |
| --- | --- |
| Main/root | System; no always-enabled callback blocking back-to-home |
| Settings/subpages, nodes, subscriptions, logs | System cross-activity transition; toolbar routes through AndroidX dispatcher |
| App routing, managed routing, mobile settings | Conditional AndroidX callback only while dirty/saving/updating; preserve keep editing/discard and busy protection |
| Subscription properties | Conditional callback while changed/processing; existing asynchronous exit confirmation and deferred profile release retained |
| Files at root | System cross-activity return |
| Nested file directories | AndroidX progress callback with compressed snapshot of actual parent directory at descent; commit pops one stack entry; cancellation restores original view transforms without changing stack/data/scroll |
| Fullscreen preference editor | ComponentDialog owns a separate dispatcher; preview of underlying Activity; cancel gesture retains edit session; committed back uses `cancel()` and existing dismissal/cancellation listeners |
| Modal bottom sheets | Material's native predictive back support; no competing Activity callback |
| Confirmation/input/progress dialogs | AppCompat/Material dialog owner; existing cancelability/listeners preserved; no Activity-wide key interception |

Confirmation callbacks intentionally do not preview abandoning unsaved work: they act only on commit and present the existing confirmation. Cancellation has no data side effects. Only system-managed back supplies home/task/activity destination animations; API 34+ supplies custom gesture progress. Android 13 keeps compatible commit handling and system previews where supported; Android 21–32 and three-button/keyboard back retain their actions.

Directory previews are read-only snapshots, bounded to 512 pixels on the longest edge, PNG-compressed in memory. No configuration is exported or written to disk. Decode/overlay resources are released on cancellation, refresh, stop, and destruction. Repeated back during a pending refresh cannot pop multiple levels. The actual directory is always re-read after commit.

## Validation and release

`Predictive Back Regression` runs existing Go routing/mobile tests, design/UI, service and app unit suites, and the ARM64 release build without signing secrets. `PredictiveBackTest` covers API 28/33/35 dispatcher behavior, both edges, cancel, repeat-back, lifecycle cleanup, toolbar dispatch, dirty/busy notifications, and cancelable/noncancelable fullscreen dialogs. `BackGuardTest` exercises the real unsaved/busy guards on API 28/35. `BackGestureSmokeTest` injects real touchscreen gestures into an isolated Android 15 emulator for settings, nodes, dirty settings, fullscreen dialogs, bottom sheets, and root back to launcher; it retains preview/after screenshots and lifecycle assertions. The CI emulator uses its own x86_64 debug APK and fresh test storage.

Before calling the APK a verified official update, check a gesture-navigation Android 14/15+ device or emulator: root to launcher, settings to parent, nodes to parent, both edges with cancellation at several distances, nested directory pop/cancel, fullscreen preference cancel/commit, bottom sheet cancel/commit, unsaved keep/discard, and save/update-in-progress. Repeat basic button back on Android 12 or lower. On Android 13/14 enable predictive back animations in developer settings; on Android 15+ system animations are enabled for opted-in apps.

The regression artifact is a candidate, **not signed with the Live key and not an official update**. Keep `com.astarion.clash.live`, existing Live signing backup and protected `weekly-live.yaml`/`weekly_live.py` workflow. No key, package identity, user storage, routing configuration, or signing/publishing control is replaced by this change. The existing release flow must increment versionCode and verify APK integrity/package/certificate before publication.

Official references:

- https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
- https://developer.android.com/guide/navigation/custom-back/support-animations-views
- https://github.com/material-components/material-components-android/blob/1.12.0/docs/foundations/PredictiveBack.md
