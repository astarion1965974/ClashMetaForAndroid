# COUI migration — ColorOS 17 only

Baseline: `80ffdf2ac808c916f350ad24f716447fb9b7770a` (`main`, 2026-11-07). This document tracks **actual code changes**, not a completed migration claim.

## Scope and inventory

- Single target environment: the owner's ColorOS 17 device. No old-Android API compatibility branches are required.
- At baseline the `design` module contained **20 *Design.kt screen controllers**, **109 production Kotlin files** and **43 XML layout resources**. A comprehensive migration still requires inspecting all of them, including shared adapters, preferences, popup windows and auxiliary activities.
- The app already had a COUI shared `Scaffold` for the three main tabs, but the profiles tab rendered an existing `RecyclerView` as an `AndroidView` without the parent scaffold's content padding.

## Implemented in this change

| File | Migration |
| --- | --- |
| `design/.../ProfilesDesign.kt` | Main-tab configuration list uses a Compose `LazyColumn` of COUI profile rows; state and refresh time update with subscriptions. |
| `design/.../adapter/ProfileAdapter.kt` | Extract reusable `ProfileListItem` COUI row to avoid a second profile-card design. |
| `design/.../ClashCouiPages.kt` | Pass shared-scaffold Insets to profiles tab; use native COUI navigation and toolbar without old OS API gates. |
| `design/.../ProxyTabs.kt` | Remove old Android SDK condition for COUI chip navigation. |
| `design/.../view/CouiIconButtonView.kt` | Remove old Android SDK condition for COUI toolbar controls. |
| `design/.../view/CouiFabView.kt` | Remove old Android SDK condition for COUI floating action buttons. |
| `design/.../util/LegacyCouiPageHost.kt` | Remove old Android SDK condition for legacy-page COUI floating actions. |
| `design/.../LiveUiTest.kt` | Add layout regression test for profile list being below the primary top bar. |

Renderer initialization error handling remains (including the simple in-app fallback where present); this protects against runtime initialization failures and headless testing rather than supporting obsolete Android versions.

## Not yet migrated / verification required

- Legacy secondary and tertiary pages still use XML layouts, `RecyclerView`, `AndroidView`, and `wrapLegacyCouiPage`. Their actual UI composition needs full page-by-page conversion rather than indiscriminately deleting Android View functionality.
- Audit all `design/src/main/java/.../design/preference/`, `dialog/`, `view/`, and `adapter/` components for remaining old controls.
- Verify exact COUI alpha7 TopAppBar API before implementing a scroll-linked large-title collapse across every page; don't substitute similarly named APIs from a different COUI library.
- Verify ColorOS 17 visual fidelity of header gradient, glass buttons, settings navigation, dialogs and dark theme **on the user's device**.
- This changes source only; no signed release is published by this branch. Run CI / device build and review test output before merging into main.

## Second batch: COUI large titles and scroll linkage

- Main home, configuration and settings tabs now use the real `LargeTopAppBar` from **ItosEO/COUI-Kit alpha7** with `CouiTopAppBarDefaults.exitUntilCollapsedScrollBehavior()`.
- Separate remembered behaviors preserve large-title scroll progress per tab; the shared Scaffold receives the selected `nestedScrollConnection`.
- Home, settings and the COUI profile `LazyColumn` now apply `state.contentPadding(padding, density)`, preserving correct card position during bar collapse. Each page still consumes original system insets.
- Standalone home/settings hosts also use native COUI large titles.
- `ColorOsLargeTopBar` adjusts visual title typography from 32sp expanded toward 24dp collapsed, instead of inheriting COUI Kit's oversized default 40sp HomeTitle.
- This does **not** complete secondary/tertiary XML page migration, which is intentionally tracked separately.


## Third batch: native secondary screens

- Introduced `ColorOsSubPage`: common COUI `Scaffold`, 32sp large/24dp compact title, shared back button, proper nested scrolling and window Insets.
- Converted **HelpDesign**, **ApkBrokenDesign** and **AppCrashedDesign** from XML-based `DesignSettingsCommonBinding` / `DesignAppCrashedBinding` pages to native Compose COUI bodies.
- Existing web links, reinstall request and selectable crash logs remain available. Added a UI regression test for these actions.
- The rest of the legacy settings, file, proxy and log pages still need native conversion. Old XML resources are kept until their remaining references have been audited.

## Main tab state retention

- The configuration tab now hoists `LazyListState` to the stable three-tab host alongside the existing home and settings scroll states. Switching the translucent bottom navigation away and back no longer recreates the configuration scroll position.

## Shared legacy preference elements now rendered in COUI

- Converted `PreferenceScreen.category()` from `PreferenceCategoryBinding` to COUI `GroupTitle` inside `CouiPreferenceView`.
- Converted `PreferenceScreen.tips()` from `PreferenceTipsBinding` to COUI typography, preserving its mutable `TipsPreference.text` contract.
- Added `CouiRichText` to convert Android `Spanned` bold/italic formatting into Compose `AnnotatedString`; the native Help page no longer displays literal HTML tags from translated `<strong>` strings.
- Other XML preference methods retain their existing COUI row wrappers for now. Do not delete XML resources until references and build verification confirm that they are unused.

## Fourth native secondary screen: saved logs

- `LogsDesign` now uses `ColorOsLazySubPage`: native COUI large-title header with glass back/delete-all actions, a lazy Compose list, a COUI logcat entry and COUI history rows.
- Existing `StartLogcat`, `DeleteAll`, `OpenFile` requests and deletion confirmation flow stay unchanged.
- Lists are keyed by `LogFile.fileName` to preserve stable item identity. UI test verifies logcat and saved-file requests.
- `LogFileAdapter` and the old `design_logs.xml` resource are retained until post-CI dead-code cleanup.

## Fifth native secondary screen: create profile

- `NewProfileDesign` now directly renders a COUI Compose lazy picker, replacing `DesignNewProfileBinding` and the XML `RecyclerView` with native COUI preference rows.
- File, URL, QR scan and external-source actions and the external-provider long press are retained; a UI test checks the first three requests.
- Existing legacy `ProfileProviderAdapter` is retained until all callers are verified.
- Saved Logs' logcat entry now sits within the COUI `Group` surface, matching the settings card background and round corners.

## Sixth native secondary screen: proxy/rule providers

- `ProvidersDesign` now uses `ColorOsLazySubPage` and native COUI preference rows in a Compose lazy list, with a COUI-glass toolbar refresh-all button.
- Preserves non-inline provider refresh requests, progress indicator, elapsed timestamps, `notifyUpdated()` retry and `notifyChanged()` success pathways.
- Includes a regression test for refresh-all excluding inline providers and avoiding duplicate requests while an update is already running.
- Legacy `ProviderAdapter` and `design_providers.xml` remain tracked for safe, post-verification cleanup.

## Seventh native secondary screen: live and saved log viewer

- `LogcatDesign` now uses native COUI `ColorOsLazySubPage`, with glass actions: live logs show Stop/Close; saved logs show Delete and Export. Existing activity request contract remains unchanged.
- Replaces old Android `RecyclerView` with Compose `LazyColumn`, preserving reverse-layout for live streaming, a follow-tail scroll policy and long-press-to-copy functionality.
- `ColorOsLazySubPage` now accepts optional `LazyListState` and `reverseLayout`, with defaults preserving existing callers.
- Added a viewer regression test for appended messages and action routing.
- Legacy `LogMessageAdapter` and XML layout remain temporarily until complete dead-code verification.

## Native app and network settings

- `AppSettingsDesign` and `NetworkSettingsDesign` now use `ColorOsSubPage` and COUI `Group`, `Item`, `Switch`, `RadioButton`, and `Dialog` instead of the old `DesignSettingsCommonBinding` and `PreferenceScreen` View hierarchy.
- Added `NativeCouiPreferences.kt` for stateful native COUI switch, choice and clickable preference rows. Store writes use `Dispatchers.IO`; callback requests stay on main and controls are disabled while writes are in progress.
- App settings preserve automatic restart, appearance switch/recreate events, app icon hiding, recents, live notification/group settings, and traffic notifications. ColorOS 17 exclusively targets Android 36+ so old SDK visibility branches are gone.
- Network settings preserve live VPN option lockout, dependent VPN options, tun stack choice, and deep links to application routing, managed routing and mobile settings.

## Native mobile network form

- Added `ColorOsFormPage` with a fixed COUI Save bar, shared large collapsing glass title, primary back action, and insets-aware scrollable content.
- `MobileNetworkDesign` no longer inflates `design_mobile_network.xml` or legacy `PreferenceScreen`. It now uses native COUI groups/switches/actions and the reusable form scaffold.
- Preserved in-memory editable settings model, dirty/back interception, Save/BindWifi/Permissions/AppRouting events, Wi-Fi bind/unbind, node multi-select (max five), save disabled and reconnect labels.

## Native managed routing form

- Converted `ManagedRoutingDesign` from legacy `DesignManagedRoutingBinding`/View-based `preferenceScreen` to the shared native `ColorOsFormPage`, preserving fixed Save, dirty-state back handling, running/update lockout and provider timestamps.
- All seven routing category targets use COUI `Group`/`Item`, retaining the existing target-menu contract and `DIRECT`/`REJECT`/default semantics. Exceptions edit in the existing COUI validation dialog, and reset uses COUI confirm.
- Existing rule-provider refresh/update and save requests still flow through `ManagedRoutingActivity`; backend logic unchanged.

## Native app routing access control

- `AccessControlDesign` now renders COUI `ColorOsLazyFormPage` with the existing Compose `AccessControls` filters and a lazy list of COUI checkbox rows. Removed XML `DesignAccessControlBinding` and legacy RecyclerView adapter from the screen.
- Bulk operations, installed-app filtering, sorting, import/export, VPN selection modes, live selected count and save/reconnect requests continue using the original `AccessControlActivity` contract.
- Removed the unused View constructor parameter from `AccessControlMenu`; its COUI dialog options and request channel are untouched.
- Tests for application routing and the managed-routing save footer were migrated from XML View-ID assumptions to Compose semantics.

## Native configuration file manager

- `FilesDesign` now hosts an entirely COUI Compose lazy list, native glass Add toolbar action, COUI file/folder cards with more menus, and stable file IDs. Old `DesignFilesBinding`, `FileAdapter`, dialog binding and `RecyclerView` no longer participate in the screen.
- Directory back-stack handling and preview capture, root-level import restrictions, rename/delete/export requests, file-name validation, and elapsed-update behavior are preserved.

## Native configuration properties editor

- `PropertiesDesign` now renders editable configuration name/URL/AGE key/update interval/file links as native COUI rows, plus a shared glass Save header action. Profile edits continue to use validated input dialogs and the same update/commit request channel.
- `ColorOsSubPage` now accepts an optional COUI toolbar action slot, reused by properties and future subordinate pages.
- The progress and fetch-status callback contract is unchanged; the page's `progressing` flag now uses Compose state instead of an XML binding boolean.
- The mobile network UI test's old XML `save_view` assertion was updated to use the COUI save-label semantics.

## Native proxy selection screen

- `ProxyDesign` uses a full Compose COUI scaffold, collapsible blurred large title, shared glass toolbar buttons, COUI chip tab groups, and a virtualized proxy card grid for 1/2/3 columns, replacing the legacy ViewPager2 + RecyclerView UI tree.
- Existing `ProxyActivity` requests (`ReloadAll`, `Reload`, `Select`, `UrlTest`, `PatchMode`, `ReLaunch`) and mutable parent/linked group states are retained. `requestRedrawVisible()` explicitly triggers Compose recomposition after a selector changes.
- Removed the unused legacy menu View argument from `ProxyMenu`; filter, mode, layout and sorting options still use COUI dialog menus.
- The former floating URL-test FAB is represented by the same COUI glass top-bar button; a dedicated bottom-floating FAB can be restored if necessary after ColorOS 17 visual verification.

## Dead XML screen layouts removed

- Deleted 12 old `design_*.xml` screen layouts after migrating their owners to Compose; these layouts no longer have active UI callers. This prevents stale Data Binding generation from compiling classes/method references for eliminated View controllers.
- Remaining layouts are retained until their actual owning pages and dialogs are migrated and audited; the legacy adapter and View resources are not deleted wholesale.

## Native override value editors

- Added `NativeCouiValueEditor` and typed COUI native editors for nullable port, text, string-list and map override fields. The dialogs distinguish an explicit null Reset from a real empty value and use COUI typography and controls; this replaces the View-based editable-list / map editing pathway for migrated pages.
- Multi-line lists use one value per line; maps use `key=value` pairs. Port editor validates 0–65535 before saving. Existing core `ConfigurationOverride` property references remain the data source.

## Data Binding KAPT compatibility cleanup

- The obsolete `dialog_files_menu.xml` still referenced deleted `FilesDesign.requestRename/requestExport/requestImport/requestDelete` Java methods after the page moved to `showCouiMenu`. The old dialog is now removed to prevent Data Binding from generating invalid callbacks. No active screen imports `DialogFilesMenuBinding`.

## Native advanced override settings

- `OverrideSettingsDesign` now renders all **34** general + DNS override controls using the new native COUI nullable value, enum choice, list and map editors. The original `ConfigurationOverride` instance and deferred persistence are unchanged.
- DNS child controls are disabled when DNS strategy is explicitly false; the Reset header action still requests activity-level confirmation and override clearing.
- Old `DesignSettingsOverideBinding` and XML `PreferenceScreen` are no longer used by this controller; its resource can be removed after confirming no other owner.

## Native Meta feature settings

- `MetaFeatureSettingsDesign` now renders native COUI Groups/Items for AGE X25519/hybrid keys, four primary meta settings, fourteen sniffing options and four GeoX import actions. The existing AGE key helper already used COUI dialogs and is retained unchanged.
- Sniffer dependent fields disable when sniffing is explicitly disabled. All import/reset requests and core override property references are unchanged.
- The previously commented-out GeoX URL override controls remain inactive as before; these are not user-facing pages.

## Remove obsolete legacy-page COUI bridge

- All 20 `*Design.kt` page controllers now use `ComposeView` roots. `BaseActivity` directly hosts these roots rather than injecting the old `wrapLegacyCouiPage` AndroidView-based toolbar/adapter bridge.
- Removed `LegacyCouiPageHost.kt`, which still referenced obsolete View IDs from migrated XML layouts and failed Kotlin compilation. Root-native COUI components own their own glass toolbar, content Insets and navigation.
- This does **not** imply that every popup, dialog, or adapter XML resource has been migrated; those are audited separately.

## Complete native standalone configurations page

- `ProfilesDesign.root` no longer inflates `design_profiles.xml` via `AndroidView`; both standalone and primary configuration tabs render the same pure COUI `ProfileListItem` lazy list with shared collapse behavior, floating bottom navigation and glass actions.
- Removed `ProfileAdapter` RecyclerView and obsolete XML dialogs/row resources. `ProfileListItem` now lives in its own reusable Composable file.
- Updated list state is Compose-observed, including update-all progress, elapsed time and active profile. QR sharing remains a native COUI dialog with bitmap rendering; old Data Binding menu callbacks are eliminated.

## Native COUI Activity-layer dialogs

- Converted unsaved changes confirmations in managed routing, application routing and mobile network activities to suspendable COUI dialogs with explicit Continue Editing/Discard labels.
- Converted ColorOS-only mobile background-location permission prompt, Wi-Fi SSID binding, notification strategy-group picker and GeoX file validation warning to COUI `Dialog` / `showCouiSingleChoice` / `showCouiAlert`.
- Removed two old Android SDK UI appearance guards from BaseActivity (ColorOS 17 target only). No change to VPN or core logic.

## UI tests adapted to native COUI contracts

- Updated `BackGuardTest` to validate that saving/updating blocks page exit and dirty edits do not finish without COUI confirmation, instead of assuming Material AlertDialog objects exist.
- Updated `PredictiveBackTest` to click the actual COUI back icon through Compose semantics, not removed XML `activity_bar_close_view`; targeting Android 35+ only in this test.
- Updated the managed-routing UI smoke test to wait for COUI composition and check the Save control exists in the native semantics tree.

## Eliminate obsolete adapter-specific UI tests and adopt COUI interactions

- `CouiPreferenceTest` now validates native COUI switches, drawable-bearing COUI rows, profile selection/menu separation and COUI proxy card enabled/disabled interactions. The old tests no longer instantiate obsolete `ProfileAdapter`, `ProxyAdapter`, `PreferenceScreen` or `ActionLabel` Views.
- The trailing row icon action now uses COUI `couiButtonFeedback` and Compose button semantics instead of a plain legacy ripple/clickable surface.
