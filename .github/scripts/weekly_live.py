#!/usr/bin/env python3
"""Trusted weekly CI helpers. Build source is kept separate from signing credentials."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile
from datetime import datetime, timezone

PACKAGE = 'com.astarion.clash.live'
SIGNER = '60687fc4e622e84d7f1db5e3acc5db12487b48bc7a10d691426df60e815a9b7f'
NAME_PATTERN = r'(?m)^(\s*versionName\s*=\s*)"[^"]+"'
CODE_PATTERN = r'(?m)^(\s*versionCode\s*=\s*)\d+'


def run(args, cwd=None, env=None):
    return subprocess.check_output(list(map(str, args)), cwd=cwd, env=env, text=True).strip()


def git(root, *args):
    return run(['git', *args], root)


def versions(text):
    names = re.findall(r'(?m)^\s*versionName\s*=\s*"([^"]+)"', text)
    codes = re.findall(r'(?m)^\s*versionCode\s*=\s*(\d+)', text)
    if len(names) != 1 or len(codes) != 1:
        raise ValueError('Expected exactly one versionName and versionCode assignment')
    return names[0], int(codes[0])


def replace_versions(text, name, code):
    versions(text)
    if not re.fullmatch(r'[A-Za-z0-9.\-]+', name) or not 0 < code < 2100000000:
        raise ValueError('Invalid Android version')
    text = re.sub(NAME_PATTERN, lambda m: m[1] + '"' + name + '"', text)
    return re.sub(CODE_PATTERN, lambda m: m[1] + str(code), text)


def output(name, value):
    print(f'{name}: {value}')
    if os.getenv('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as f:
            f.write(f'{name}={value}\n')


def summary(text):
    if os.getenv('GITHUB_STEP_SUMMARY'):
        with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as f:
            f.write(text + '\n')


def prepare(root, artifact, upstream, force=False, published_head=''):
    root, artifact = Path(root).resolve(), Path(artifact).resolve()
    artifact.mkdir(parents=True, exist_ok=True)
    original = git(root, 'rev-parse', 'HEAD')
    upstream_sha = git(root, 'rev-parse', upstream)
    ancestor = subprocess.run(['git', 'merge-base', '--is-ancestor', upstream_sha, original], cwd=root).returncode == 0
    if ancestor and original == published_head and not force:
        output('build', 'false')
        summary('上游和已发布定制版均无变化，本周无需重复编译。')
        return None
    path = root / 'build.gradle.kts'
    original_text = path.read_text()
    _, old_code = versions(original_text)
    git(root, 'config', 'user.name', 'github-actions[bot]')
    git(root, 'config', 'user.email', '41898282+github-actions[bot]@users.noreply.github.com')
    if not ancestor:
        # Normalize only the two release-version lines before merging. Other conflicts must stop CI.
        base = git(root, 'merge-base', original, upstream_sha)
        base_name, base_code = versions(git(root, 'show', base + ':build.gradle.kts'))
        normalized = replace_versions(original_text, base_name, base_code)
        if normalized != original_text:
            path.write_text(normalized)
            git(root, 'add', 'build.gradle.kts')
            git(root, 'commit', '-m', 'Normalize release metadata before upstream merge')
        merged = subprocess.run(['git', 'merge', '--no-ff', '--no-edit', upstream_sha], cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        if merged.returncode:
            conflicts = git(root, 'diff', '--name-only', '--diff-filter=U')
            (artifact / 'merge-conflicts.txt').write_text(conflicts + '\n')
            git(root, 'merge', '--abort')
            git(root, 'reset', '--hard', original)  # isolated CI checkout only
            summary('上游合并冲突，保留当前定制分支与已发布 APK。冲突文件见诊断产物。')
            raise RuntimeError('Upstream merge conflict; live branch was not changed')
    merged_name, merged_code = versions(path.read_text())
    version_code = max(old_code, merged_code) + 1
    # Keep upstream release identity, but use a unique Live build label and monotonic Android code.
    release_base = merged_name.split('.Live')[0]
    day = datetime.now(timezone.utc).strftime('%Y%m%d')
    version_name = f'{release_base}.Live.Weekly.{day}.{os.getenv("GITHUB_RUN_NUMBER", "0")}'
    path.write_text(replace_versions(path.read_text(), version_name, version_code))
    git(root, 'add', 'build.gradle.kts')
    git(root, 'commit', '-m', f'Weekly Live build {version_name} ({version_code})')
    git(root, 'submodule', 'sync', '--recursive')
    git(root, 'submodule', 'update', '--init', '--recursive')
    sdk = os.getenv('ANDROID_HOME') or os.getenv('ANDROID_SDK_ROOT')
    if sdk:
        (root / 'local.properties').write_text(f'sdk.dir={sdk}\ncustom.application.id={PACKAGE}\ncustom.application.name=Clash Meta Live\nremove.suffix=true\n')
    metadata = dict(original=original, candidate=git(root, 'rev-parse', 'HEAD'), upstream=upstream_sha,
                    versionName=version_name, versionCode=version_code, package=PACKAGE, signer=SIGNER)
    (artifact / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
    output('build', 'true')
    output('version_code', version_code)
    summary(f'准备构建 `{version_name}`；上游 `{upstream_sha[:12]}`。')
    return metadata


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def android_tool(name):
    sdk = Path(os.getenv('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    return sdk / 'build-tools/35.0.0' / name


def verify_identity(apk, metadata, check_signer=False):
    info = run([android_tool('aapt'), 'dump', 'badging', apk])
    expected = f"package: name='{PACKAGE}' versionCode='{metadata['versionCode']}' versionName='{metadata['versionName']}'"
    if expected not in info or "native-code: 'arm64-v8a'" not in info:
        raise RuntimeError('APK identity/version/architecture mismatch')
    if check_signer:
        cert = run([android_tool('apksigner'), 'verify', '--verbose', '--print-certs', apk])
        if SIGNER not in cert:
            raise RuntimeError('APK does not use the existing Live signing identity')
    run([android_tool('zipalign'), '-c', '-P', '16', '4', apk])
    with zipfile.ZipFile(apk) as z:
        if z.testzip():
            raise RuntimeError('Corrupt APK')
        manifest = json.loads(z.read('assets/managed-rules/sources.json'))
        for item in manifest['files']:
            data = z.read('assets/managed-rules/' + item['name'] + '.mrs')
            if hashlib.sha256(data).hexdigest() != item['sha256']:
                raise RuntimeError('Bundled rule checksum mismatch')


def bundle(root, artifact):
    root, artifact = Path(root).resolve(), Path(artifact).resolve()
    metadata = json.loads((artifact / 'metadata.json').read_text())
    git(root, 'diff', '--exit-code')
    git(root, 'diff', '--cached', '--exit-code')
    if git(root, 'rev-parse', 'HEAD') != metadata['candidate']:
        raise RuntimeError('Source changed during build')
    candidates = list((root / 'app/build/outputs/apk/alpha/release').glob('*-arm64-v8a-*.apk'))
    if len(candidates) != 1:
        raise RuntimeError('Expected exactly one ARM64 APK')
    apk = artifact / 'build-output.apk'
    apk.write_bytes(candidates[0].read_bytes())
    verify_identity(apk, metadata)
    git(root, 'bundle', 'create', artifact / 'candidate.bundle', metadata['original'] + '..HEAD')
    metadata['apk_sha256'] = sha(apk)
    metadata['bundle_sha256'] = sha(artifact / 'candidate.bundle')
    (artifact / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')


def sign(artifact, private):
    artifact, private = Path(artifact).resolve(), Path(private).resolve()
    private.mkdir(parents=True, mode=0o700, exist_ok=True)
    metadata = json.loads((artifact / 'metadata.json').read_text())
    for key in ['original', 'candidate', 'upstream']:
        if not re.fullmatch('[0-9a-f]{40}', metadata[key]):
            raise ValueError('Invalid source identity')
    if metadata['package'] != PACKAGE or metadata['signer'] != SIGNER:
        raise ValueError('Unexpected signing request')
    if sha(artifact / 'build-output.apk') != metadata['apk_sha256'] or sha(artifact / 'candidate.bundle') != metadata['bundle_sha256']:
        raise ValueError('Build artifact checksum mismatch')
    raw = base64.b64decode(''.join(os.environ['LIVE_SIGNING_BACKUP_B64'].split()), validate=True)
    archive = private / 'backup.zip'
    archive.write_bytes(raw)
    archive.chmod(0o600)
    try:
        with zipfile.ZipFile(archive) as z:
            key_name = next(n for n in z.namelist() if n.endswith('.jks'))
            prop_name = next(n for n in z.namelist() if n.endswith('signing.properties'))
            key = private / 'live.jks'
            key.write_bytes(z.read(key_name))
            key.chmod(0o600)
            props = dict(line.split('=', 1) for line in z.read(prop_name).decode().splitlines() if '=' in line and not line.startswith('#'))
        env = os.environ.copy()
        env['LIVE_STORE_PASSWORD'] = props['keystore.password']
        env['LIVE_KEY_PASSWORD'] = props['key.password']
        target = artifact / 'ClashMeta-Live-arm64-v8a.apk'
        subprocess.run([str(android_tool('apksigner')), 'sign', '--ks', str(key), '--ks-key-alias', props['key.alias'],
                        '--ks-pass', 'env:LIVE_STORE_PASSWORD', '--key-pass', 'env:LIVE_KEY_PASSWORD',
                        '--out', str(target), str(artifact / 'build-output.apk')], env=env, check=True)
        verify_identity(target, metadata, check_signer=True)
        (artifact / 'SHA256SUMS').write_text(sha(target) + '  ' + target.name + '\n')
    finally:
        for name in ['backup.zip', 'live.jks']:
            (private / name).unlink(missing_ok=True)


def publish(root, artifact):
    root, artifact = Path(root).resolve(), Path(artifact).resolve()
    metadata = json.loads((artifact / 'metadata.json').read_text())
    verify_identity(artifact / 'ClashMeta-Live-arm64-v8a.apk', metadata, check_signer=True)
    if sha(artifact / 'candidate.bundle') != metadata['bundle_sha256']:
        raise ValueError('Source bundle checksum mismatch')
    for field in ['original', 'candidate', 'upstream']:
        if not re.fullmatch('[0-9a-f]{40}', metadata[field]):
            raise ValueError('Invalid Git identity')
    branch = 'feat/live-notifications'
    candidate_ref = 'refs/heads/live-ci-candidate'
    git(root, 'fetch', artifact / 'candidate.bundle', 'HEAD:' + candidate_ref)
    if git(root, 'rev-parse', candidate_ref) != metadata['candidate']:
        raise ValueError('Wrong bundled commit')
    git(root, 'merge-base', '--is-ancestor', metadata['original'], metadata['candidate'])
    current = git(root, 'ls-remote', 'origin', 'refs/heads/' + branch).split()[0]
    if current not in [metadata['original'], metadata['candidate']]:
        raise RuntimeError('Live branch changed while building; refusing to replace newer custom changes')
    # Make the commit available for a draft release, without advancing the Live branch yet.
    temporary = 'live-ci/candidate-' + str(metadata['versionCode'])
    git(root, 'push', 'origin', candidate_ref + ':refs/heads/' + temporary)
    tag = 'live-weekly-' + str(metadata['versionCode'])
    repo = os.environ['GITHUB_REPOSITORY']
    notes = artifact / 'release-notes.md'
    notes.write_text('Weekly Clash Meta Live build\n\n'
        + 'Version: ' + metadata['versionName'] + ' (' + str(metadata['versionCode']) + ')\n'
        + 'Upstream: ' + metadata['upstream'] + '\n'
        + 'Source: ' + metadata['candidate'] + '\n\n'
        + 'Includes Live notifications, managed routing and per-app controls. Same Live signing identity.\n')
    exists = subprocess.run(['gh', 'release', 'view', tag, '--repo', repo], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    if not exists:
        run(['gh', 'release', 'create', tag, '--repo', repo, '--draft', '--target', metadata['candidate'],
             '--title', metadata['versionName'], '--notes-file', notes])
    run(['gh', 'release', 'upload', tag, '--repo', repo, '--clobber',
         artifact / 'ClashMeta-Live-arm64-v8a.apk', artifact / 'SHA256SUMS', artifact / 'metadata.json'])
    # Recheck after upload. A normal fast-forward push also rejects any concurrent custom commit.
    current = git(root, 'ls-remote', 'origin', 'refs/heads/' + branch).split()[0]
    if current != metadata['candidate']:
        if current != metadata['original']:
            raise RuntimeError('Live branch advanced during release upload')
        git(root, 'push', 'origin', candidate_ref + ':refs/heads/' + branch)
    run(['gh', 'release', 'edit', tag, '--repo', repo, '--draft=false', '--latest'])
    git(root, 'push', 'origin', '--delete', temporary)
    summary('已发布同签名 Live APK：https://github.com/' + repo + '/releases/latest/download/ClashMeta-Live-arm64-v8a.apk')


def main():
    p = argparse.ArgumentParser()
    p.add_argument('command', choices=['prepare', 'bundle', 'sign', 'publish'])
    p.add_argument('--source', default='source')
    p.add_argument('--artifact', default='weekly-artifact')
    p.add_argument('--upstream', default='refs/remotes/upstream/main')
    p.add_argument('--private', default=os.path.join(os.getenv('RUNNER_TEMP', '/tmp'), 'live-signing'))
    p.add_argument('--force', action='store_true')
    a = p.parse_args()
    if a.command == 'prepare':
        prepare(a.source, a.artifact, a.upstream, a.force, os.getenv('LIVE_PUBLISHED_HEAD', ''))
    elif a.command == 'bundle':
        bundle(a.source, a.artifact)
    elif a.command == 'sign':
        sign(a.artifact, a.private)
    else:
        publish(a.source, a.artifact)


if __name__ == '__main__':
    main()
