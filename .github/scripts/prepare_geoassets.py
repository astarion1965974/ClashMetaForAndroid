#!/usr/bin/env python3
"""Prepare verified GeoData once, independently from Gradle Android builds.

Order: SHA-256 verified workspace/cache -> previously signed Live Release APK ->
upstream MetaCubeX assets. Never silently ship missing or truncated files.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
from pathlib import Path

REPO = "astarion1965974/ClashMetaForAndroid"
ASSETS = {
    "geoip.metadb": "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geoip.metadb",
    "geosite.dat": "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/geosite.dat",
    "ASN.mmdb": "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/GeoLite2-ASN.mmdb",
    "BundleMRS.7z": "https://github.com/MetaCubeX/meta-rules-dat/releases/download/latest/BundleMRS.7z",
}
RELEASE_APK = "ClashMeta-Live-arm64-v8a.apk"
SIGNING_SHA256 = "60687fc4e622e84d7f1db5e3acc5db12487b48bc7a10d691426df60e815a9b7f"
ROOT = Path(__file__).resolve().parents[2]
ASSET_DIR = ROOT / "app/src/main/assets"
MANIFEST = ROOT / ".geoassets/sha256sums"


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def sound_asset(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size <= 1024:
        return False
    if path.name == "BundleMRS.7z":
        with path.open("rb") as stream:
            return stream.read(6) == bytes.fromhex("377abcaf271c")
    return True


def checksums(directory: Path) -> dict[str, str]:
    return {name: digest(directory / name) for name in ASSETS}


def verified(directory: Path, manifest: Path) -> bool:
    try:
        rows = [line for line in manifest.read_text(encoding="utf-8").splitlines() if line]
        pairs = [line.split("  ", 1) for line in rows]
        recorded = {name: sha for sha, name in pairs}
        if len(pairs) != len(ASSETS) or set(recorded) != set(ASSETS):
            return False
        if not all(len(sha) == 64 and all(c in "0123456789abcdef" for c in sha)
                   for sha in recorded.values()):
            return False
        return all(sound_asset(directory / name) and digest(directory / name) == recorded[name]
                   for name in ASSETS)
    except (OSError, ValueError):
        return False


def install(directory: Path, asset_dir: Path, manifest: Path) -> None:
    if not all(sound_asset(directory / name) for name in ASSETS):
        raise ValueError("Incomplete or corrupt GeoData bundle; refusing to package")
    asset_dir.mkdir(parents=True, exist_ok=True)
    manifest.parent.mkdir(parents=True, exist_ok=True)
    # Publish manifest last. Interrupted installations cannot pass the next verification.
    hashes = checksums(directory)
    for name in ASSETS:
        staged = asset_dir / (name + ".pending")
        shutil.copyfile(directory / name, staged)
        os.replace(staged, asset_dir / name)
    temporary = manifest.with_suffix(".pending")
    temporary.write_text("".join(f"{hashes[name]}  {name}\n" for name in sorted(ASSETS)), encoding="utf-8")
    os.replace(temporary, manifest)
    if not verified(asset_dir, manifest):
        raise RuntimeError("GeoData validation failed after installation")


def restore_from_signed_release(directory: Path) -> bool:
    sdk = os.environ.get("ANDROID_HOME")
    if not sdk or not os.environ.get("GH_TOKEN"):
        print("No Android SDK/GH_TOKEN; skipping signed Release APK fallback")
        return False
    signer = Path(sdk) / "build-tools/36.0.0/apksigner"
    if not signer.is_file():
        print("No Android apksigner; skipping signed Release APK fallback")
        return False
    with tempfile.TemporaryDirectory(prefix="cmfa-previous-apk-") as temp:
        apk_dir = Path(temp)
        try:
            subprocess.run(
                ["gh", "release", "download", "--repo", REPO,
                 "--pattern", RELEASE_APK, "--dir", str(apk_dir)],
                check=True, capture_output=True, text=True, timeout=240,
            )
            apk = apk_dir / RELEASE_APK
            certificate = subprocess.check_output(
                [str(signer), "verify", "--print-certs", str(apk)],
                text=True, timeout=120,
            ).lower()
            if SIGNING_SHA256 not in certificate:
                raise ValueError("Previous Release APK signing certificate mismatch")
            with zipfile.ZipFile(apk) as archive:
                for name in ASSETS:
                    with archive.open("assets/" + name) as source, (directory / name).open("wb") as target:
                        shutil.copyfileobj(source, target, 1024 * 1024)
            if not all(sound_asset(directory / name) for name in ASSETS):
                raise ValueError("Previous Release APK does not contain all valid GeoData assets")
            print("Recovered GeoData from previously signed stable Release APK")
            return True
        except (OSError, ValueError, RuntimeError, KeyError, subprocess.SubprocessError,
                zipfile.BadZipFile) as exc:
            print(f"Signed Release bootstrap unavailable: {exc}", file=sys.stderr)
            return False


def download_from_upstream(directory: Path) -> None:
    for name, url in ASSETS.items():
        success = False
        for attempt in range(1, 5):
            staged = directory / (name + ".download")
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "ClashMetaLive-GeodataCache/1"})
                with urllib.request.urlopen(request, timeout=90) as response, staged.open("wb") as target:
                    shutil.copyfileobj(response, target, 1024 * 1024)
                if not sound_asset(staged):
                    # Validate by logical asset name rather than .download suffix.
                    if staged.stat().st_size <= 1024 or (
                        name == "BundleMRS.7z" and staged.open("rb").read(6) != bytes.fromhex("377abcaf271c")
                    ):
                        raise ValueError(f"{name}: upstream payload is invalid")
                os.replace(staged, directory / name)
                print(f"Fetched {name} from upstream (attempt {attempt})")
                success = True
                break
            except (OSError, ValueError) as exc:
                staged.unlink(missing_ok=True)
                print(f"{name}: download attempt {attempt}/4 failed: {exc}", file=sys.stderr)
                if attempt < 4:
                    time.sleep(attempt * 3)
        if not success:
            raise RuntimeError(f"Could not obtain valid {name} from upstream or previous Release")


def self_test() -> None:
    with tempfile.TemporaryDirectory(prefix="cmfa-geo-test-") as tmp:
        base = Path(tmp)
        src, dest, manifest = base / "src", base / "assets", base / "sha256sums"
        src.mkdir()
        for name in ASSETS:
            payload = (bytes.fromhex("377abcaf271c") if name == "BundleMRS.7z" else b"DATA") + b"x" * 4096
            (src / name).write_bytes(payload)
        install(src, dest, manifest)
        assert verified(dest, manifest), "valid cache should be accepted"
        (dest / "geoip.metadb").write_bytes(b"tampered" * 600)
        assert not verified(dest, manifest), "tampered cache must be rejected"
        (src / "BundleMRS.7z").write_bytes(b"not-7z" * 1000)
        try:
            install(src, dest, manifest)
        except ValueError:
            pass
        else:
            raise AssertionError("invalid 7z signature accepted")
    print("GeoData manifest, corruption, and archive validation tests passed")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--refresh", action="store_true", help="force download of new upstream data")
    parser.add_argument("--self-test", action="store_true", help="offline integrity regression tests")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return
    if verified(ASSET_DIR, MANIFEST) and not args.refresh:
        print("Using SHA-256 verified cached GeoData: no download required")
        return
    if args.refresh:
        print("Refreshing GeoData from upstream")
    else:
        print("GeoData cache missing or invalid; trying previously signed Release first")
    with tempfile.TemporaryDirectory(prefix="cmfa-geodata-") as temp:
        candidate = Path(temp)
        if args.refresh:
            download_from_upstream(candidate)
        elif not restore_from_signed_release(candidate):
            download_from_upstream(candidate)
        install(candidate, ASSET_DIR, MANIFEST)
    print("GeoData bundle is ready and SHA-256 verified")


if __name__ == "__main__":
    main()
