#!/usr/bin/env python3
"""Bundle Three.js into the APK so the game runs with no network access.

three.module.min.js (r167+) is NOT self-contained — it imports ./three.core.min.js.
Both files must be present or the ES-module path fails and the game silently
falls back to the older 0.160.1 classic build.
"""
from pathlib import Path
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'
FILES = {
    'three.module.min.js': 'https://cdn.jsdelivr.net/npm/three@0.185.1/build/three.module.min.js',
    'three.core.min.js':   'https://cdn.jsdelivr.net/npm/three@0.185.1/build/three.core.min.js',
    'three.min.js':        'https://cdnjs.cloudflare.com/ajax/libs/three.js/0.160.1/three.min.js',
}
for name, url in FILES.items():
    out = ASSETS / name
    print('download', url)
    out.write_bytes(urlopen(url, timeout=60).read())
    print('saved', out, out.stat().st_size)

missing = [n for n in FILES if not (ASSETS / n).exists()]
if missing:
    raise SystemExit('missing bundled assets: ' + ', '.join(missing))
