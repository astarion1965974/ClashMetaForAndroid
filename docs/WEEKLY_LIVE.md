# Weekly Live upstream builds

Schedule: Monday 01:15 UTC / 09:15 Asia/Shanghai. Workflow: `.github/workflows/weekly-live.yaml`, on the default `main` branch. The customized source remains on `feat/live-notifications`.

The trusted control branch fetches `MetaCubeX/ClashMetaForAndroid:main`, preserves Live modifications and follows the core submodule revision selected by the Android upstream. It does not independently jump to the newest Mihomo Alpha commit. A first build, unpublished custom change, upstream update or manual force request triggers native routing/mobile tests, Android view tests, service unit tests and an ARM64 release build. If both upstream and the published Live source are unchanged, compilation is skipped.

A merge conflict or test/build/validation failure leaves the remote Live branch and existing APK release intact. Only versionName/versionCode lines are normalized before merging to avoid routine release-number conflicts. All other merge conflicts stop the job and produce diagnostics. Android versionCode is greater than both previous Live and merged upstream codes.

Compilation runs without signing credentials or repository write permissions. An isolated publishing job signs the built APK and verifies the existing Live certificate, application ID, inner APK version, ARM64 architecture, 16 KB ZIP alignment and bundled MRS checksums before publishing. It also refuses to overwrite a Live branch changed during the build. The inherited upstream-only publishing workflows are disabled in this personal fork to avoid duplicate builds and incompatible signing paths.

## One-time activation

1. Open the repository Actions tab and enable GitHub Actions if this newly forked repository asks for activation.
2. Under Settings → Secrets and variables → Actions, add the repository secret `LIVE_SIGNING_BACKUP_B64`. Its value is the single-line Base64 encoding of the existing `ClashMeta-Live-signing-backup.zip` (the ZIP contains `clashmeta-live.jks` and `signing.properties`). Never commit the ZIP, key, password or encoded value to Git.
3. Open Weekly Live Upstream Build and choose Run workflow to verify the first remote build. `force_build` is available for a same-source rebuild. To retry only signing/publication after fixing credentials or runner configuration, set `publish_run_id` to the earlier successful build run ID; leave it blank for normal weekly runs. CI control-file pushes do not automatically rebuild the app.

With an authenticated GitHub CLI, the signing secret can be set without displaying its content:

```bash
python3 -c 'import base64,pathlib; print(base64.b64encode(pathlib.Path("ClashMeta-Live-signing-backup.zip").read_bytes()).decode())' | gh secret set LIVE_SIGNING_BACKUP_B64 --repo astarion1965974/ClashMetaForAndroid
```

Missing signing credentials do not publish a random debug-signed replacement. The build artifact is retained for 30 days, and the Actions summary explicitly states that no installable Live update was published. The Live branch remains unchanged until signing and publishing are available.

After the first successful signed release, the fixed public APK download URL is:

https://github.com/astarion1965974/ClashMetaForAndroid/releases/latest/download/ClashMeta-Live-arm64-v8a.apk

GitHub schedules are best effort and may be delayed; scheduled workflows in inactive public repositories can be disabled after 60 days. Open the workflow page to re-enable them when necessary.

## CI helper checks

Run `python3 .github/scripts/test_weekly_live.py`. These tests create isolated temporary Git repositories and cover upstream merges, retained custom changes, version monotonicity, conflict rollback, no-change skipping, first publication and manual rebuilds.

Mobile maintenance hooks use a generated Go overlay; the upstream submodule remains unchanged. The build fails if upstream hook locations change, so unsupported merges cannot be published.
