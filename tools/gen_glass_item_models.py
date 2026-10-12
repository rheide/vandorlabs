#!/usr/bin/env python3
"""Generate padded inventory models for all saved regular/diagonal glass finishes."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ids = re.search(r'IDS=\{([^}]+)', (ROOT / 'src/main/java/com/vandorlabs/tiles/GlassTextures.java').read_text())[1]
ids = re.findall(r'"([^"]+)"', ids)
out = ROOT / 'src/main/resources/assets/vandorlabs/models/item/configured'
for diagonal in (False, True):
    for size in range(1 if diagonal else 2):
        for name in ids:
            detail = 'medium' if diagonal or size else 'low'
            texture = f'vandorlabs:blocks/programmable_glass/{detail}/{name}'
            if diagonal:
                model = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item/programmable_diagonal_glass.json').read_text())
                model['textures'] = {k: texture for k in model['textures']}
            else:
                model = {'parent': f'vandorlabs:item/{detail}/space_glass_{detail}', 'textures': {'pane': texture}}
            model['display'] = {'gui': {'rotation': [30, 225, 0], 'scale': [.6, .6, .6]}}
            (out / f'glass_{"diagonal" if diagonal else "regular"}_{size}_{name}.json').write_text(json.dumps(model, indent=2) + '\n')
print('Generated 57 padded glass item variants')
