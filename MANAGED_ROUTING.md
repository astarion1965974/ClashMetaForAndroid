# Live3: application routing and managed rules

- Network → 应用分流: unified capture mode, name/package search, selected and system filters, per-row effective outcome, visible-result bulk actions, explicit save/reconnect, unsaved-change confirmation.
- Network → 规则管理: opt-in managed rules, category exits, direct/proxy domain exceptions, optional ad blocking, domestic encrypted DNS policy, cached rule updates and template reset. Disable managed routing to restore subscription routing on the next load.
- Proxy page → `Live · 默认代理`: choose the default proxy node. Category exits can instead select an existing subscription strategy group. Start a profile before opening rule management to list its groups.
- Capturing an Android app means entering the local Clash VPN. Its connections still follow DIRECT/proxy/reject rules. Application bypass only bypasses this device's Clash VPN.

## Configuration boundaries

The original imported `config.yaml` is never rewritten. The native load processor reads the independent `files/clash/managed-routing.json`, builds a managed rule chain and adds a default selector after normal override processing. Proxy definitions, proxy providers, original groups and other settings are retained. Existing rule providers are also retained for DNS/sniffer references. Managed mode loads in rule mode; original mode preserves subscription/override mode.

Settings use an atomic file. Draft UI changes are saved explicitly. Updates retain the previous cached rule on failure. Managed MRS files live under `files/clash/managed-rules/`, independent of the selected subscription. A removed subscription group falls back to that category's default policy without deleting the user's stored preference.

Priority: domain exceptions → private domains/IPs → optional ads → AI → media → social → development → CN domains → foreign domains → CN IPs → fallback. Domain/IP categorization inherits upstream coverage and can be corrected with local domain exceptions.

## Rule sources

MRS: https://github.com/MetaCubeX/meta-rules-dat, `meta/geo/`. The bundled snapshot's exact commit, paths and SHA-256 checksums are in `core/src/main/assets/managed-rules/sources.json`. Updates follow the upstream meta branch every 24 hours while active; manual updates are available on the rule management page.

The upstream README is included as attribution in the assets. Its CN domain set derives from blackmatrix7/ios_rule_script ChinaMax_Domain; other domain categories and IP data follow the upstream aggregation. Do not claim all domains or all OpenClash router functions are covered. Android uses its VpnService backend, not OpenClash's router firewall.

## Validation

`service:testAlphaReleaseUnitTest`: notification selection and per-app capture/bulk selection policies.

From `core/src/main/golang`: `go test ./native/routing -count=1 -v`: original mode unchanged, priorities, exceptions, stale targets, stable caches, invalid domains/collisions, real Mihomo parsing of every bundled MRS and representative domestic/overseas domain routing.

Release: same Live signing identity/application ID; version 2.11.36.Live3 / 211038. ARM64 APK. UI interaction and Android VPN reconnection still require device testing.

## Live3.1 serialization fix

The Live3 release used reflective serializer lookup for managed settings. R8 removed the indirectly referenced generated serializer, so saving a template failed with `Serializer for class 'ManagedRoutingSettings' is not found`. Live3.1 (versionCode 211039) explicitly references the generated serializer for both encoding and decoding, and encodes before opening the atomic write. Five codec regression tests cover every persisted field, native-consumer defaults, older partial files, unknown fields and malformed values. An R8-minified store reproduction fails on the old code and passes the save/reopen/disable cycle with the fix.

## Live3.2 compact notification region

The promoted notification's short critical text now shows only the selected main-group node's country/region (for example 香港 or 日本), inferred from its flag, country/region name or common subscription abbreviation. Expanded notification titles continue to show the full node name. Ambiguous or unidentified labels show the localized proxy status; direct, blocked, updating and multi-route states remain explicit. This is the region advertised by the node name, not an outbound-IP geolocation measurement. VersionCode 211040 supports in-place upgrades from Live3.1.
