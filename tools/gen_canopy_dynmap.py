#!/usr/bin/env python3
"""Register tile-aware closed canopy overview models and their four materials."""
import json
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
textures += [f'block:id=%{name},state=*' + ''.join(
    f',patch{i}=0:canopy_{m}' for i, m in enumerate(materials)) +
    ',transparency=SEMITRANSPARENT' for name in ids]
for filename, lines in [('dynmap-models.txt', models), ('dynmap-texture.txt', textures)]:
    path = ASSETS / filename
    path.write_text(path.read_text().split(MARKER)[0].rstrip() + '\n\n' + MARKER + '\n'.join(lines) + '\n')
