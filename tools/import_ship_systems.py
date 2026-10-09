"""Import the ship systems model kit into the host mod's resource namespace."""
import argparse
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('kit', type=Path)
    kit = parser.parse_args().kit
    models = json.loads((kit / 'model_catalog.json').read_text(encoding='utf-8'))
    assert len(models) == 18
    assets = ROOT / 'src/main/resources/assets/vandorlabs'
    for model in models:
        model['footprint'] = [model['occupancy_xyz'][0], model['occupancy_xyz'][2]]
        faces = [face for face in model['faces'] if face['material'] != 'glass']
        # The source exports each cuboid as six consecutive rectangular faces.
        assert len(faces) % 6 == 0
        boxes = []
        for offset in range(0, len(faces), 6):
            group = faces[offset:offset + 6]
            vertices = [v for face in group for v in face['v']]
            low = [min(v[i] for v in vertices) for i in range(3)]
            high = [max(v[i] for v in vertices) for i in range(3)]
            assert all(high[i] > low[i] for i in range(3))
            assert all(all(v[i] in (low[i], high[i]) for i in range(3)) for v in vertices)
            boxes.append(low + high)
        for face in model['faces']:
            if face['material'] == 'glass':
                low = [min(v[i] for v in face['v']) for i in range(3)]
                high = [max(v[i] for v in face['v']) for i in range(3)]
                axis = next(i for i in range(3) if low[i] == high[i])
                low[axis] -= 1 / 64
                high[axis] += 1 / 64
                boxes.append(low + high)
        model['collision'] = boxes
        name = model['id']
        for folder, data in [
                ('blockstates', {'variants': {'normal': {'model': 'vandorlabs:ship_system_empty'}}}),
                ('models/item', {'parent': 'block/block'})]:
            (assets / folder / (name + '.json')).write_text(json.dumps(data) + '\n', encoding='utf-8')
        # Unique small recipes; large variants upgrade their matching small machine.
        index = models.index(model) // 2
        if model['size'] == 'small':
            ingredients = [{'item': 'vandorlabs:programmable_matter_ingot'},
                           {'item': 'minecraft:redstone', 'data': 0}]
            ingredients += [{'item': 'minecraft:iron_ingot'}] * (index % 3 + 1)
            ingredients += [{'item': 'minecraft:gold_ingot'}] * (index // 3 + 1)
        else:
            ingredients = [{'item': 'vandorlabs:' + name.replace('_large', '_small')}]
            ingredients += [{'item': 'vandorlabs:programmable_matter_ingot'}] * 2
            ingredients += [{'item': 'minecraft:iron_block'}]
        recipe = {'type': 'minecraft:crafting_shapeless', 'ingredients': ingredients,
                  'result': {'item': 'vandorlabs:' + name}}
        (assets / 'recipes' / (name + '.json')).write_text(json.dumps(recipe) + '\n', encoding='utf-8')
    (assets / 'data/ship_system_meshes.json').write_text(json.dumps(models, separators=(',', ':')) + '\n', encoding='utf-8')
    (assets / 'models/block/ship_system_empty.json').write_text('{"textures":{"particle":"vandorlabs:blocks/ship_systems/alloy"},"elements":[]}\n', encoding='utf-8')
    target = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/ship_systems'
    target.mkdir(parents=True, exist_ok=True)
    for texture in (kit / 'forge/resources/assets/shipsystems/textures/blocks').glob('*.png'):
        shutil.copyfile(texture, target / texture.name)
    lang = ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang'
    text = lang.read_text(encoding='utf-8')
    text = '\n'.join(line for line in text.splitlines() if not any(line.startswith('tile.' + m['id'] + '.') for m in models))
    text += '\n' + '\n'.join('tile.' + m['id'] + '.name=' + m['label'].replace(' · ', ' ') for m in models) + '\n'
    lang.write_text(text, encoding='utf-8')
    print('Imported 18 ship systems with authored collision cuboids and recipes')


if __name__ == '__main__':
    main()
