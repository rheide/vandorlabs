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
finishes = re.findall(r'new Finish\("([^"]+)", "([^"]+)"\)', java)
ids = [entry[0] for entry in finishes]
textures = [entry[1] for entry in finishes]
assert ids
models = root / 'src/main/resources/assets/vandorlabs/models/item'
out = models / 'configured'
out.mkdir(exist_ok=True)
for block in ('programmable_block', 'programmable_trigger_block',
              'programmable_slab', 'programmable_wall',
              'programmable_diagonal_wall', 'programmable_porthole_wall',
              'programmable_porthole_block', 'programmable_trapdoor',
              'programmable_diagonal_trapdoor'):
    base = json.loads((models / (block + '.json')).read_text())
    for choice, texture in zip(ids, textures):
        model = json.loads(json.dumps(base))
        for key, value in model['textures'].items():
            if value in ('vandorlabs:blocks/dark_wall_panel', 'vandorlabs:blocks/imported/trapdoors/cyan_lit_armored_sci_fi_hatch_4'):
                model['textures'][key] = 'vandorlabs:blocks/' + texture
        (out / (block + '_' + choice + '.json')).write_text(
            json.dumps(model, indent=2) + '\n')
for style in ('fit', 'tile'):
    base = json.loads((out / ('programmable_slab_dark_wall_panel_' + style + '.json')).read_text())
    for choice, texture in zip(ids, textures):
        model = json.loads(json.dumps(base))
        for key, value in model['textures'].items():
            if value in ('vandorlabs:blocks/dark_wall_panel', 'vandorlabs:blocks/imported/trapdoors/cyan_lit_armored_sci_fi_hatch_4'):
                model['textures'][key] = 'vandorlabs:blocks/' + texture
        (out / ('programmable_slab_' + choice + '_' + style + '.json')).write_text(
            json.dumps(model, indent=2) + '\n')
print('Generated', len(ids) * 11, 'configured item models')
