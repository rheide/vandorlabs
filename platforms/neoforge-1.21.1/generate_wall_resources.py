"""Translate canonical flat wall models, stock glass artwork and configured drops."""
import json
import re
from canonical_resources import CanonicalResources
from compact_runtime_assets import resized as resize_runtime_art

WALL_IDS = ('programmable_wall', 'programmable_porthole_wall', 'programmable_porthole_block')


def generate(root, resources):
    canonical = CanonicalResources(root, resources)
    for family in WALL_IDS:
        canonical.block(family)
        loot = json.loads((resources / 'data/vandorlabs/loot_table/blocks/programmable_block.json').read_text())
        loot = json.loads(json.dumps(loot).replace('vandorlabs:programmable_block', 'vandorlabs:' + family))
        canonical.write(resources / f'data/vandorlabs/loot_table/blocks/{family}.json', loot)
    source = (root / 'src/portable/java/com/vandorlabs/tiles/GlassTextures.java').read_text()
    ids = re.findall(r'"([a-z_]+)"', re.search(r'IDS=\{([^}]+)\}', source).group(1))
    for shade in ids:
        for suffix in ('', '_mild', '_strong') if shade != 'clear' else ('',):
            # Forge stages the Medium master at 128px and aliases Low to it.
            # Resizing the separate Low derivative changes the authored pixels.
            name = shade + suffix
            canonical.texture('vandorlabs:blocks/programmable_glass/low/' + name,
                              source_value='vandorlabs:blocks/programmable_glass/medium/' + name)
            resize_runtime_art(canonical.assets / f'textures/block/programmable_glass/low/{name}.png', 128)
    # The canonical inventory pane also follows Forge's standard per-block artwork.
    resize_runtime_art(canonical.assets / 'textures/block/space_doors/medium/glass_tile.png', 128)
    canonical.texture('vandorlabs:blocks/programmable_glass/metal_side')
    for tag in ('mineable/pickaxe', 'needs_stone_tool'):
        path = resources / f'data/minecraft/tags/block/{tag}.json'
        value = json.loads(path.read_text())
        value['values'] = list(dict.fromkeys(value['values'] + ['vandorlabs:' + family for family in WALL_IDS]))
        canonical.write(path, value)
