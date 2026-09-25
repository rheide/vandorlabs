#!/usr/bin/env python3
"""Generate inventory models for the configured housing choices.

Run after changing ScreenHousingTextures; models remain checked in so Forge 1.12
can bake every NBT-selected variant during model registration.
"""
import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
java = (root / 'src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java').read_text()
arrays = re.findall(r'\{([^{}]+)\};', java, re.S)
ids = re.findall(r'"([^"]+)"', arrays[0])
textures = re.findall(r'"([^"]+)"', arrays[1])
assert len(ids) == len(textures)
models = root / 'src/main/resources/assets/vandorlabs/models/item'
out = models / 'configured'
out.mkdir(exist_ok=True)
for block in ('programmable_block', 'programmable_trigger_block',
              'programmable_slab', 'programmable_wall',
              'programmable_diagonal_wall', 'programmable_porthole_wall'):
    base = json.loads((models / (block + '.json')).read_text())
    for choice, texture in zip(ids, textures):
        model = json.loads(json.dumps(base))
        for key, value in model['textures'].items():
            if value == 'vandorlabs:blocks/dark_wall_panel':
                model['textures'][key] = 'vandorlabs:blocks/' + texture
        (out / (block + '_' + choice + '.json')).write_text(
            json.dumps(model, indent=2) + '\n')
print('Generated', len(ids) * 6, 'configured item models')
