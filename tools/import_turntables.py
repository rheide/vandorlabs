#!/usr/bin/env python3
"""Import turntable face artwork and generate directional block/item models."""
import argparse, io, json, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/vandorlabs'
ART = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/turntable'
DISPLAY = {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [.5, .5, .5]}}

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

def model(name, textures, formed=False):
    faces = {side: {'texture': '#' + side, 'cullface': side}
             for side in textures if side != 'particle' and not (formed and side == 'up')}
    write(ASSETS / f'models/block/{name}.json', {
        'textures': textures, 'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}],
        'display': DISPLAY})

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    ART.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.archive) as archive:
        faces = [n for n in archive.namelist() if '/assets/vandorlabs/textures/block/' in n and n.endswith('.png')]
        if len(faces) != 17:
            raise ValueError('Expected the 17 supplied turntable face textures')
        for name in faces:
            path = ART / Path(name).name
            data = archive.read(name)
            with Image.open(io.BytesIO(data)) as source:
                surface = Path(name).stem in ('turntable_block_top', 'turntable_block_top_on')
                if source.size != ((512, 512) if surface else (64, 64)):
                    raise ValueError('Unexpected face dimensions: ' + name)
                path.write_bytes(data)
                if surface:
                    source.resize((64, 64), Image.Resampling.LANCZOS).save(path.with_name(path.stem + '_low.png'))
    prefix = 'vandorlabs:blocks/turntable/'
    variants = {}
    for cw in (False, True):
        for active in (False, True):
            suffix = ('cw' if cw else 'ccw') + ('_on' if active else '_off')
            name = 'turntable_controller_' + suffix
            textures = {side: prefix + 'turntable_controller_' + texture for side, texture in {
                'north': 'front_' + suffix, 'south': 'back', 'west': 'left', 'east': 'right',
                'up': 'top_' + ('on' if active else 'off'), 'down': 'bottom', 'particle': 'back'}.items()}
            model(name, textures)
            for facing, rotation in {'north': 0, 'east': 90, 'south': 180, 'west': 270}.items():
                variants[f'active={str(active).lower()},clockwise={str(cw).lower()},facing={facing}'] = {'model': 'vandorlabs:' + name, 'y': rotation}
    write(ASSETS / 'blockstates/turntable_controller.json', {'variants': variants})
    variants = {}
    for formed in (False, True):
        name = 'turntable_surface' + ('_formed' if formed else '')
        textures = {side: prefix + 'turntable_block_' + texture for side, texture in {
            'north': 'front', 'south': 'back', 'west': 'left', 'east': 'right',
            'up': 'top_low', 'down': 'bottom', 'particle': 'front'}.items()}
        model(name, textures, formed)
        for facing, rotation in {'up': (0, 0), 'down': (180, 0), 'north': (90, 0), 'east': (90, 90), 'south': (90, 180), 'west': (90, 270)}.items():
            variants[f'facing={facing},formed={str(formed).lower()}'] = {'model': 'vandorlabs:' + name, 'x': rotation[0], 'y': rotation[1]}
    write(ASSETS / 'blockstates/turntable_surface.json', {'variants': variants})
    for name, parent in [('turntable_controller', 'turntable_controller_cw_off'), ('turntable_surface', 'turntable_surface')]:
        write(ASSETS / f'models/item/{name}.json', {'parent': 'vandorlabs:block/' + parent, 'display': DISPLAY})
        write(ASSETS / f'recipes/{name}.json', {
            'type': 'minecraft:crafting_shaped',
            'pattern': ['MMM', 'MRM', 'MMM'] if name.endswith('controller') else ['MM', 'MM'],
            'key': dict({'M': {'item': 'vandorlabs:programmable_matter_ingot'}}, **({'R': {'item': 'minecraft:redstone'}} if name.endswith('controller') else {})),
            'result': {'item': 'vandorlabs:' + name, 'count': 1 if name.endswith('controller') else 4}})
    print('Imported 17 faces, derived two low-resolution surface states, generated directional models and recipes')

if __name__ == '__main__':
    main()
