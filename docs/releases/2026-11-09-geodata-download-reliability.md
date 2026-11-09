# GeoData download reliability hotfix (2026-11-09)

This release incorporates [PR #16](https://github.com/astarion1965974/ClashMetaForAndroid/pull/16) after the R8.32 Android assembly was interrupted by transient GitHub CDN HTTP 504 responses during `:app:downloadGeoFiles`.

- Retry each GeoData asset independently, with capped backoff and explicit connection/read timeouts.
- Download to a temporary sibling file, then promote only non-empty successful downloads to the expected asset path.
- Preserve GeoData source URLs, asset names, routing configuration and the existing Android package/signing identity.
- Preserve all R8.32 ColorOS 17 UI improvements and per-domain DNS upstream diagnostics.

Verification: PR native DNS and Android JVM checks completed successfully. The mainline release workflow must independently pass routing/mobile/DNS tests, JVM tests, ARM64 R8 assembly, candidate package validation, signing-certificate verification, zip alignment and stable Release publication. Do not treat this document as evidence that the final APK has been published.

The stable version suffix is allocated by the GitHub Actions mainline run number, not manually fixed in this note.
