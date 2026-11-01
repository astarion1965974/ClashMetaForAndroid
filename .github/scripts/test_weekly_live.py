#!/usr/bin/env python3
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('weekly_live', Path(__file__).with_name('weekly_live.py'))
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class WeeklyLiveTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'source'
        self.root.mkdir()
        self.artifact = Path(self.temp.name) / 'artifact'
        self.git('init', '-b', 'main')
        self.git('config', 'user.name', 'CI fixture')
        self.git('config', 'user.email', 'fixture@example.test')
        self.gradle = self.root / 'build.gradle.kts'
        self.gradle.write_text('android {\n    versionName = "2.11.36"\n    versionCode = 211036\n\n\n    compileSdk = 35\n}\n')
        (self.root / 'custom.txt').write_text('base\n')
        self.git('add', '.')
        self.git('commit', '-m', 'base')
        self.base = self.git('rev-parse', 'HEAD')
        self.git('checkout', '-b', 'live')
        self.gradle.write_text(ci.replace_versions(self.gradle.read_text(), '2.11.36.Live3.2', 211040).replace('compileSdk = 35', 'compileSdk = 36'))
        (self.root / 'custom.txt').write_text('live notifications and managed routing\n')
        self.git('commit', '-am', 'custom Live features')
        self.live = self.git('rev-parse', 'HEAD')
        self.env = patch.dict(os.environ, {'GITHUB_OUTPUT': '', 'GITHUB_STEP_SUMMARY': '', 'ANDROID_HOME': '', 'ANDROID_SDK_ROOT': '', 'GITHUB_RUN_NUMBER': '7'})
        self.env.start()
        self.addCleanup(self.env.stop)

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True, stderr=subprocess.DEVNULL).strip()

    def upstream(self, conflict=False):
        self.git('checkout', 'main')
        self.gradle.write_text(ci.replace_versions(self.gradle.read_text(), '2.12.0', 212000))
        (self.root / 'upstream.txt').write_text('upstream improvement\n')
        if conflict:
            (self.root / 'custom.txt').write_text('incompatible upstream change\n')
        self.git('add', '.')
        self.git('commit', '-m', 'upstream update')
        result = self.git('rev-parse', 'HEAD')
        self.git('checkout', 'live')
        return result

    def test_merge_retains_custom_changes_and_uses_larger_version(self):
        upstream = self.upstream()
        meta = ci.prepare(self.root, self.artifact, upstream)
        self.assertEqual(212001, meta['versionCode'])
        self.assertTrue(meta['versionName'].startswith('2.12.0.Live.Weekly.'))
        self.assertIn('compileSdk = 36', self.gradle.read_text())
        self.assertIn('live notifications', (self.root / 'custom.txt').read_text())
        self.assertTrue((self.root / 'upstream.txt').exists())
        self.assertEqual(upstream, self.git('rev-parse', 'main'))
        self.assertEqual(self.live, meta['original'])
        self.git('merge-base', '--is-ancestor', self.live, meta['candidate'])
        self.git('merge-base', '--is-ancestor', upstream, meta['candidate'])

    def test_conflict_restores_previous_source_and_keeps_diagnostics(self):
        upstream = self.upstream(conflict=True)
        before = self.gradle.read_text()
        with self.assertRaisesRegex(RuntimeError, 'merge conflict'):
            ci.prepare(self.root, self.artifact, upstream)
        self.assertEqual(self.live, self.git('rev-parse', 'HEAD'))
        self.assertEqual(before, self.gradle.read_text())
        self.assertIn('live notifications', (self.root / 'custom.txt').read_text())
        self.assertIn('custom.txt', (self.artifact / 'merge-conflicts.txt').read_text())
        self.assertFalse((self.root / '.git/MERGE_HEAD').exists())

    def test_already_published_source_is_not_rebuilt(self):
        self.assertIsNone(ci.prepare(self.root, self.artifact, self.base, published_head=self.live))
        self.assertEqual(self.live, self.git('rev-parse', 'HEAD'))

    def test_force_rebuild_and_first_publication_increment_live_code(self):
        meta = ci.prepare(self.root, self.artifact, self.base, force=True, published_head=self.live)
        self.assertEqual(211041, meta['versionCode'])
        self.assertIn('Live.Weekly.', meta['versionName'])

    def test_unpublished_custom_change_is_built_without_new_upstream(self):
        meta = ci.prepare(self.root, self.artifact, self.base, published_head=self.base)
        self.assertEqual(211041, meta['versionCode'])

    def test_build_cannot_bundle_staged_uncommitted_source(self):
        meta = ci.prepare(self.root, self.artifact, self.base, force=True)
        (self.root / 'custom.txt').write_text('changed after tests\n')
        self.git('add', 'custom.txt')
        with self.assertRaises(subprocess.CalledProcessError):
            ci.bundle(self.root, self.artifact)

    def test_ambiguous_version_schema_is_rejected(self):
        with self.assertRaises(ValueError):
            ci.versions('versionName = "one"\nversionCode = 1\nversionCode = 2\n')

    def test_version_values_cannot_inject_gradle_or_exceed_android_limit(self):
        for name, code in [('bad"value', 1), ('valid', 2100000000), ('valid', -1)]:
            with self.assertRaises(ValueError):
                ci.replace_versions(self.gradle.read_text(), name, code)


if __name__ == '__main__':
    unittest.main(verbosity=2)
