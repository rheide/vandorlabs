"""Reuse the canonical programmable slab and stair shapes and item transforms."""
import json
from canonical_resources import CanonicalResources


def generate(root, resources):
    canonical = CanonicalResources(root, resources)
    for family in ('programmable_slab', 'programmable_stairs'):
        canonical.block(family)
        loot = json.loads((resources / 'data/vandorlabs/loot_table/blocks/programmable_block.json').read_text())
        loot = json.loads(json.dumps(loot).replace('vandorlabs:programmable_block', 'vandorlabs:' + family))
        canonical.write(resources / f'data/vandorlabs/loot_table/blocks/{family}.json', loot)
    for tag in ('mineable/pickaxe', 'needs_stone_tool'):
        path = resources / f'data/minecraft/tags/block/{tag}.json'
        value = json.loads(path.read_text())
        value['values'] = list(dict.fromkeys(value['values'] + ['vandorlabs:programmable_slab', 'vandorlabs:programmable_stairs']))
        canonical.write(path, value)
