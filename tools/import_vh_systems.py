"""Import Vektor/Hadron state meshes, textures, items and recipes."""
import argparse
import json
from pathlib import Path
from import_ship_systems import ROOT, prepare_models
from kit_textures import import_textures
from console_depth_models import extend_consoles
from machine_accents import apply_accents


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kit', type=Path)
    kit = parser.parse_args().kit
    models = json.loads((kit / 'model_catalog.json').read_text(encoding='utf-8'))
    assert len(models) == 52
    assets = ROOT / 'src/main/resources/assets/vandorlabs'
    prepare_models(models, json.loads((assets / 'data/vh_system_meshes.json').read_text(encoding='utf-8')))
    source = extend_consoles(models, kit, ROOT / 'build/vh-texture-source')
    items = []
    for model in models:
        model['footprint'] = [model['occupancy_xyz'][0], model['occupancy_xyz'][2]]
        for face in model['faces']:
            face['material'] = 'vhtech/' + face['material']
        if model['active'] or model['console_width'] > 1 or model.get('console_depth', 1) > 1:
            continue
        name = model['stem'] if not model['console_width'] else model['manufacturer'] + '_console'
        model['block_id'] = name
        items.append((name, model))
        for folder, data in [('blockstates', {'variants': {'normal': {'model': 'vandorlabs:ship_system_empty'}}}),
                             ('models/item', {'parent': 'block/block'})]:
            (assets / folder / (name + '.json')).write_text(json.dumps(data) + '\n', encoding='utf-8')
        if name.endswith('_large'):
            ingredients = [{'item': 'vandorlabs:' + name.replace('_large', '_small')}]
            ingredients += [{'item': 'vandorlabs:programmable_matter_ingot'}] * 2
            ingredients += [{'item': 'minecraft:iron_block'}]
        else:
            category = ['gravity', 'tractor', 'dampener', 'analysis', 'core', 'console'].index(model['category'])
            ingredients = [{'item': 'vandorlabs:programmable_matter_ingot'}, {'item': 'minecraft:quartz'},
                           {'item': 'minecraft:dye', 'data': 10 if model['manufacturer'] == 'vektor' else 4}]
            ingredients += [{'item': 'minecraft:iron_ingot'}] * (category % 3 + 1)
            ingredients += [{'item': 'minecraft:gold_ingot'}] * (category // 3 + 1)
        recipe = {'type': 'minecraft:crafting_shapeless', 'ingredients': ingredients, 'result': {'item': 'vandorlabs:' + name}}
        (assets / 'recipes' / (name + '.json')).write_text(json.dumps(recipe) + '\n', encoding='utf-8')
    target = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/ship_systems/vhtech'
    target.mkdir(parents=True, exist_ok=True)
    assert source.is_dir()
    import_textures(models, source, target, 'vhtech/')
    apply_accents(models, target, 'vhtech/')
    (assets / 'data/vh_system_meshes.json').write_text(json.dumps(models, separators=(',', ':')) + '\n', encoding='utf-8')
    lang = ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang'
    lines = [line for line in lang.read_text(encoding='utf-8').splitlines()
             if not any(line.startswith('tile.' + name + '.') for name, _ in items)]
    for name, model in items:
        label = model['label'].replace(' · OFF', '').replace(' · ', ' ')
        if model['console_width']:
            label = model['manufacturer'].capitalize() + ' Navigation Console'
        lines.append('tile.' + name + '.name=' + label)
    lang.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    print(f'Imported {len(models)} state meshes and 22 items, including rectangular Vektor consoles.')


if __name__ == '__main__':
    main()
