#!/usr/bin/env python3
from pathlib import Path
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app' / 'src' / 'main' / 'assets'
FILES = {
    'three.module.min.js': 'https://cdn.jsdelivr.net/npm/three@0.185.1/build/three.module.min.js',
    'three.min.js': 'https://cdnjs.cloudflare.com/ajax/libs/three.js/0.160.1/three.min.js',
}
for name, url in FILES.items():
    out = ASSETS / name
    print('download', url)
    out.write_bytes(urlopen(url, timeout=60).read())
    print('saved', out, out.stat().st_size)
