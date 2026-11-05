#!/usr/bin/env python3
"""Compare native palette pixels to the existing standard runtime staging path."""
import hashlib
from pathlib import Path
import shutil
import sys
import tempfile
from PIL import Image
from generate_panel_resources import generate_glass_palette

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from compact_runtime_assets import standardize


def main():
    with tempfile.TemporaryDirectory(prefix='vandor-glass-palette-') as temporary:
        base = Path(temporary)
        entries = generate_glass_palette(ROOT, base / 'native')
        assert len(entries) == 75 and len({entry['resource'] for entry in entries}) == 75
        legacy = base / 'legacy/textures'
        for entry in entries:
            stem = entry['resource'].split('/')[-1]
            for tier in ('medium', 'low'):
                relative = Path('assets/vandorlabs/textures/blocks/programmable_glass') / tier / (stem + '.png')
                source = next(ROOT / pack / relative for pack in ('texture-packs/additional', 'texture-packs/default', 'src/main/resources') if (ROOT / pack / relative).is_file())
                target = legacy / 'blocks/programmable_glass' / tier / (stem + '.png')
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
        standardize(legacy, [])
        digest = hashlib.sha256()
        for entry in entries:
            stem = entry['resource'].split('/')[-1]
            with Image.open(base / 'native/assets/vandorlabs/textures/glass-palette' / (stem + '.png')) as native, Image.open(legacy / 'blocks/programmable_glass/low' / (stem + '.png')) as old:
                assert native.size == old.size == (128, 128), stem
                assert native.convert('RGBA').tobytes() == old.convert('RGBA').tobytes(), stem
                digest.update(stem.encode())
                digest.update(native.convert('RGBA').tobytes())
        print('Glass palette PASS75 exact standardized low-path RGBA,128px, SHA256=' + digest.hexdigest())


if __name__ == '__main__':
    main()
