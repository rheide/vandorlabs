#!/usr/bin/env python3
"""Copy the released Segmented Door panel; edge/pane artwork uses shared native assets."""
import json
from pathlib import Path
from PIL import Image
ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/segmented-resources'
source = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/segmented/segmented_panel.png'
target = OUT / 'assets/vandorlabs/textures/segmented-artwork/panel.png'
with Image.open(source) as image:
    if image.size != (32, 32):
        raise ValueError('Released Segmented Door panel dimensions changed')
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(source.read_bytes())


def write(relative, value):
    path = OUT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(',', ':')) + '\n')


write('assets/vandorlabs/models/block/native_segmented_empty.json', {
    'ambientocclusion': False,
    'textures': {'particle': 'vandorlabs:segmented-artwork/panel'}, 'elements': [],
})
write('assets/vandorlabs/blockstates/segmented_door.json', {
    'variants': {'': {'model': 'vandorlabs:block/native_segmented_empty'}},
})
item = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/models/item/segmented_door.json').read_text())
item['textures'] = {'panel': 'vandorlabs:segmented-artwork/panel',
                    'edge': 'vandorlabs:frame-default/metal_side',
                    'particle': 'vandorlabs:segmented-artwork/panel'}
write('assets/vandorlabs/models/item/segmented_door.json', item)
recipe = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/recipes/segmented_door.json').read_text())
recipe['result']['id'] = recipe['result'].pop('item')
recipe['neoforge:conditions'] = [{'type': 'neoforge:item_exists', 'item': 'vandorlabs:segmented_door'}]
write('data/vandorlabs/recipe/segmented_door.json', recipe)
write('data/vandorlabs/loot_table/blocks/segmented_door.json', {
    'type': 'minecraft:block', 'pools': [{'rolls': 1,
        'entries': [{'type': 'minecraft:item', 'name': 'vandorlabs:segmented_door'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
})
