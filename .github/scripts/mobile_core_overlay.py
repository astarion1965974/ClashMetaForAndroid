#!/usr/bin/env python3
"""Generate a Go overlay without changing the upstream submodule."""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
def build():
    out = ROOT / 'core/build/mobile-overlay'
    out.mkdir(parents=True, exist_ok=True)
    changes = {
        'tunnel/statistic/tracker.go': [
            ('type Tracker interface {', 'var TrackOpened = func(*TrackerInfo) {}\n\ntype Tracker interface {'),
            ('\tmanager.Join(t)\n\treturn t', '\tmanager.Join(t)\n\tTrackOpened(t.TrackerInfo)\n\treturn t'),
            ('\tmanager.Join(ut)\n\treturn ut', '\tmanager.Join(ut)\n\tTrackOpened(ut.TrackerInfo)\n\treturn ut'),
        ],
        'adapter/provider/healthcheck.go': [
            ('func (hc *HealthCheck) process() {', 'var BackgroundHealthCheckAllowed = func() bool { return true }\n\nfunc (hc *HealthCheck) process() {'),
            ('\tgo hc.check()\n', '\tif BackgroundHealthCheckAllowed() { go hc.check() }\n'),
            ('if !hc.lazy || since < hc.interval {', 'if BackgroundHealthCheckAllowed() && (!hc.lazy || since < hc.interval) {'),
        ],
        'adapter/outboundgroup/groupbase.go': [
            ('func (gb *GroupBase) onDialFailed(adapterType C.AdapterType, err error, fn func()) {', 'var BackgroundDialCheckAllowed = func() bool { return true }\n\nfunc (gb *GroupBase) onDialFailed(adapterType C.AdapterType, err error, fn func()) {\n if !BackgroundDialCheckAllowed() { return }'),
        ],
        'component/resource/fetcher.go': [
            ('func (f *Fetcher[V]) pullLoop(forceUpdate bool) {', 'var BackgroundUpdateAllowed = func(updatedAt time.Time) bool { return true }\n\nfunc (f *Fetcher[V]) pullLoop(forceUpdate bool) {'),
            ('if forceUpdate {', 'if forceUpdate && BackgroundUpdateAllowed(f.updatedAt) {'),
            ('case <-timer.C:\n\t\t\tf.updateWithLog()', 'case <-timer.C:\n\t\t\tif !BackgroundUpdateAllowed(f.updatedAt) { timer.Reset(6 * time.Hour); continue }\n\t\t\tf.updateWithLog()'),
        ],
    }
    overlay = {}
    for name, replacements in changes.items():
        src = ROOT / 'core/src/foss/golang/clash' / name
        text = src.read_text()
        for before, after in replacements:
            if text.count(before) != 1:
                raise RuntimeError(f'Upstream maintenance hook changed: {name}: {before!r}')
            text = text.replace(before, after)
        dest = out / Path(name).name
        dest.write_text(text)
        overlay[str(src)] = str(dest)
    target = out / 'overlay.json'
    target.write_text(json.dumps({'Replace': overlay}))
    return target
if __name__ == '__main__': print(build())
