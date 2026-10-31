#!/usr/bin/env python3
"""Register tile-aware closed canopy overview models and their four materials."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/vandorlabs'
MARKER = '# Generated canopy overview models\n'
ids = [entry['id'] for entry in json.loads(
    (ROOT / 'generated-resources/assets/vandorlabs/data/blocks.json').read_text())
    if entry.get('class') == 'BlockCanopy']
materials = ['metal', 'glass', 'amber', 'cyan']
models = [f'customblock:id=%{name},state=*,class=com.vandorlabs.dynmap.CanopyRenderer'
          for name in ids]
files = {'metal': 'canopy_canopy_steel', 'glass': 'canopy_canopy_glass',
         'amber': 'canopy_canopy_amber', 'cyan': 'canopy_canopy_light'}
textures = [f'texture:id=canopy_{m},filename=assets/vandorlabs/textures/blocks/canopy/{files[m]}.png,xcount=1,ycount=1'
            for m in materials]
glass_ids = ['clear', 'soft_cyan', 'soft_dark_grey', 'white', 'orange', 'magenta',
             'light_blue', 'yellow', 'lime', 'pink', 'grey', 'light_grey', 'cyan',
             'purple', 'blue', 'brown', 'green', 'red', 'black']
finishes = [(shade, tint, name + suffix) for shade, name in enumerate(glass_ids)
            for tint, suffix in enumerate(('_mild', '', '_strong', '_opaque'))]
textures += [f'texture:id=canopy_glass_{shade}_{tint},filename=assets/vandorlabs/textures/blocks/programmable_glass/medium/{name}.png,xcount=1,ycount=1'
             for shade, tint, name in finishes]
textures += [f'block:id=%{name},state=*' + ''.join(
    f',patch{i}=0:canopy_{m}' for i, m in enumerate(materials)) + ''.join(
    f',patch{4 + shade * 4 + tint}=0:canopy_glass_{shade}_{tint}' for shade, tint, _ in finishes) +
    ',transparency=SEMITRANSPARENT' for name in ids]
for filename, lines in [('dynmap-models.txt', models), ('dynmap-texture.txt', textures)]:
    path = ASSETS / filename
    before, found, after = path.read_text().partition(MARKER)
    tail = ''
    if found:
        index = after.find('\n# Generated ')
        if index >= 0:
            tail = after[index:].lstrip('\n')
    result = before.rstrip() + '\n\n' + MARKER + '\n'.join(lines) + '\n'
    if tail:
        result += '\n' + tail
    if '--check' in sys.argv:
        if result != path.read_text():
            raise SystemExit(f'Outdated canopy map declarations: {path.name}')
    else:
        path.write_text(result)
