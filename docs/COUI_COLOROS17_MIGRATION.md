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
