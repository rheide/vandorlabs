#!/usr/bin/env python3
"""Generate the native material slice from canonical legacy models, recipes and artwork."""
import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/matter-resources'
sys.path.insert(0, str(ROOT / 'tools'))
from compact_runtime_assets import resized


def write(relative, value):
    path = OUT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n')


def model(relative):
    value = json.loads((ROOT / 'src/main/resources/assets/vandorlabs' / relative).read_text())
    if value.get('parent', '').startswith(('block/', 'item/')):
        value['parent'] = 'minecraft:' + value['parent']
    value['textures'] = {key: texture.replace(':blocks/', ':block/').replace(':items/', ':item/')
                         for key, texture in value.get('textures', {}).items()}
    write('assets/vandorlabs/' + relative, value)


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    ids = ['programmable_matter_block', 'programmable_matter_ingot', 'industrial_alloy_ingot']
    for name in ids:
        model('models/item/' + name + '.json')
    model('models/block/programmable_matter_block.json')
    write('assets/vandorlabs/blockstates/programmable_matter_block.json',
          {'variants': {'': {'model': 'vandorlabs:block/programmable_matter_block'}}})
    sources = {
        'textures/block/matter/matter.png': 'texture-packs/additional/assets/vandorlabs/textures/blocks/matter/matter.png',
        **{'textures/item/' + name + '.png': 'texture-packs/default/assets/vandorlabs/textures/items/' + name + '.png'
           for name in ids[1:]},
    }
    for target, source in sources.items():
        output = OUT / 'assets/vandorlabs' / target
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / source, output)
        resized(output, 128)
    lang = {}
    wanted = ['itemGroup.vandorlabs', 'tile.vandorlabs.programmable_matter_block.name', 'tile.vandorlabs.programmable_block.name', 'tile.vandorlabs.programmable_slab.name', 'tile.vandorlabs.programmable_stairs.name', 'tile.vandorlabs.programmable_wall.name', 'tile.vandorlabs.programmable_porthole_wall.name', 'tile.vandorlabs.programmable_trigger_block.name', 'tile.vandorlabs.push_button.name', 'tile.vandorlabs.rocker_switch.name', 'tile.vandorlabs.compact_power_lever.name', 'tile.vandorlabs.industrial_power_lever.name', 'tile.vandorlabs.small_power_lever.name', 'tile.vandorlabs.large_power_lever.name', 'tile.vandorlabs.thruster_lever.name', 'tile.vandorlabs.airliner_throttle.name', 'tile.vandorlabs.fighter_throttle.name', 'tile.vandorlabs.wall_slider.name', 'tile.vandorlabs.redstone_transmitter_block.name', 'tile.vandorlabs.redstone_receiver_block.name', 'item.vandorlabs.configurizer.name'] + [
        'item.vandorlabs.' + name + '.name' for name in ids[1:]]
    for line in (ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines():
        key, separator, value = line.partition('=')
        if separator and key in wanted:
            lang[key.replace('tile.', 'block.', 1).removesuffix('.name')] = value
    if len(lang) != len(wanted):
        raise ValueError('Missing canonical material translations')
    write('assets/vandorlabs/lang/en_us.json', lang)
    for name in ['programmable_matter_block', 'programmable_matter_unpack', 'industrial_alloy_ingot']:
        value = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/recipes' / (name + '.json')).read_text())
        value['result']['id'] = value['result'].pop('item')
        write('data/vandorlabs/recipe/' + name + '.json', value)
    for tag in ['mineable/pickaxe', 'needs_stone_tool']:
        write('data/minecraft/tags/block/' + tag + '.json', {'replace': False, 'values': ['vandorlabs:programmable_matter_block','vandorlabs:programmable_block','vandorlabs:programmable_slab','vandorlabs:programmable_stairs','vandorlabs:programmable_wall','vandorlabs:programmable_porthole_wall','vandorlabs:programmable_trigger_block','vandorlabs:push_button','vandorlabs:rocker_switch','vandorlabs:compact_power_lever','vandorlabs:industrial_power_lever','vandorlabs:small_power_lever','vandorlabs:large_power_lever','vandorlabs:thruster_lever','vandorlabs:airliner_throttle','vandorlabs:fighter_throttle','vandorlabs:wall_slider','vandorlabs:redstone_transmitter_block','vandorlabs:redstone_receiver_block']})
    write('data/vandorlabs/loot_table/blocks/programmable_matter_block.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item',
        'name': 'vandorlabs:programmable_matter_block'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write('pack.mcmeta', {'pack': {'pack_format': 34, 'description': 'Vandor Labs native material slice'}})


if __name__ == '__main__':
    main()
