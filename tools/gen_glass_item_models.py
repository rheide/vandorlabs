#!/usr/bin/env python3
"""Generate padded inventory models for all saved regular/diagonal glass finishes."""
import copy
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
            for tint, suffix in enumerate(('_mild', '', '_strong', '_opaque')):
                texture = f'vandorlabs:blocks/programmable_glass/{detail}/{name}{suffix}'
                for framed in (False, True):
                    if diagonal:
                        model = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item/programmable_diagonal_glass.json').read_text())
                        model['textures'] = {k: texture for k in model['textures']}
                        model['elements'] = model['elements'][:1]
                        # Inventory silhouettes always have a frame, including Bare world panels.
                        # Rails and pane occupy disjoint volumes, avoiding coplanar icon faces.
                        model['textures']['frame'] = 'vandorlabs:blocks/space_doors/medium/double_frame_metal'
                        pane = model['elements'][0]
                        pane['from'][0] = pane['from'][1] = 1
                        pane['to'][0] = pane['to'][1] = 15
                        pane['from'][2], pane['to'][2] = 7, 9
                        for face in ('north', 'south'):
                            pane['faces'][face]['uv'] = [1, 1, 15, 15]
                        for lower, upper in (([0, 0, 6], [1, 16, 10]),
                                             ([15, 0, 6], [16, 16, 10]),
                                             ([1, 0, 6], [15, 1, 10]),
                                             ([1, 15, 6], [15, 16, 10])):
                            model['elements'].append({'from': lower, 'to': upper,
                                'rotation': copy.deepcopy(pane['rotation']),
                                'faces': {face: {'texture': '#frame', 'uv': ([0,0,.5,8] if upper[0]-lower[0]==1 else [0,0,7,.5]) if face in ('north','south') else [.16,4,.28,4.12]} for face in
                                    ('east','west','up','down','north','south')}})
                    else:
                        # Resolve the source model so Bare can remove just the metal elements.
                        model = json.loads((ROOT / f'generated-resources/assets/vandorlabs/models/item/{detail}/space_glass_medium.json').read_text())
                        model['textures']['pane'] = texture
                        if not framed:
                            model['elements'] = [e for e in model['elements']
                                if any(f['texture'] == '#pane' for f in e['faces'].values())]
                    model['display'] = {'gui': {'rotation': [30, 225, 0], 'scale': [.6, .6, .6]}}
                    # Keep old IDs for default icons; register all configurable variants separately.
                    stem = f'glass_{"diagonal" if diagonal else "regular"}_{size}_{name}'
                    (out / f'{stem}_t{tint}_f{int(framed)}.json').write_text(json.dumps(model, indent=2) + '\n')
                    if tint == 1 and framed != diagonal:
                        (out / f'{stem}.json').write_text(json.dumps(model, indent=2) + '\n')
(ROOT / 'src/main/resources/assets/vandorlabs/models/item/programmable_diagonal_glass.json').write_text(
    (out / 'glass_diagonal_0_clear.json').read_text())
print('Generated 456 padded configurable glass item variants and 57 default aliases')
