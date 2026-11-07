#!/usr/bin/env python3
"""Project the released reader frame models and standard reader pixels without placeholders."""
import hashlib
import json
from pathlib import Path
import shutil
from PIL import Image
from generate_credential_resources import companies

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/reader-resources'
ASSETS = OUT / 'assets/vandorlabs'


def generate():
    if OUT.exists():
        shutil.rmtree(OUT)
    textures, models, items, faces = {}, [], [], {}
    def texture(name):
        if name == 'minecraft:blocks/concrete_black':
            return 'minecraft:block/black_concrete'
        if not name.startswith('vandorlabs:'):
            raise ValueError('Unknown reader material ' + name)
        relative = name.split(':', 1)[1]
        target = 'vandorlabs:reader-artwork/' + relative
        if target in textures:
            return target
        for directory in ('texture-packs/additional', 'texture-packs/default', 'src/main/resources'):
            source = ROOT / directory / 'assets/vandorlabs/textures' / (relative + '.png')
            if source.is_file():
                break
        else:
            raise ValueError('Missing reader artwork ' + name)
        with Image.open(source) as image:
            image = image.convert('RGBA')
            if image.width != image.height or image.width & (image.width - 1) or image.width > 128:
                raise ValueError('Reader artwork must retain standard square dimensions ' + name)
            destination = ASSETS / 'textures/reader-artwork' / (relative + '.png')
            destination.parent.mkdir(parents=True, exist_ok=True)
            image.save(destination, optimize=True)
            textures[target] = dict(id=target, original=name, size=image.width, rgbaSha256=hashlib.sha256(image.tobytes()).hexdigest())
        return target
    for kind in ('access_card', 'data_chip'):
        # Server geometry consumes the same authored variants as the original mounted pad.
        states = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/blockstates' / (kind + '_slot.json')).read_text())
        for variant in states['variants'].values():
            variant['model'] = variant['model'].replace('vandorlabs:token_slots/', 'vandorlabs:block/token_slots/')
        destination = ASSETS / 'blockstates' / (kind + '_slot.json')
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(states, separators=(',', ':')) + '\n')
        for source in sorted((ROOT / 'src/main/resources/assets/vandorlabs/models/block/token_slots').glob(kind + '_slot_*.json')):
            value = json.loads(source.read_text())
            value['textures'] = {key: texture(item) for key, item in value['textures'].items()}
            destination = ASSETS / 'models/block/token_slots' / source.name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_text(json.dumps(value, separators=(',', ':')) + '\n')
            name = 'vandorlabs:block/token_slots/' + source.stem
            models.append(name)
            faces[name] = sum(len(element['faces']) for element in value['elements'])
        source = ROOT / 'src/main/resources/assets/vandorlabs/models/item' / (kind + '_slot.json')
        value = json.loads(source.read_text())
        value['textures'] = {key: texture(item) for key, item in value['textures'].items()}
        destination = ASSETS / 'models/item' / source.name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(value, separators=(',', ':')) + '\n')
        items.append('vandorlabs:item/' + kind + '_slot')
        loot=OUT/'data/vandorlabs/loot_table/blocks'/str(kind+'_slot.json');loot.parent.mkdir(parents=True,exist_ok=True)
        loot.write_text(json.dumps({'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'vandorlabs:'+kind+'_slot'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]},separators=(',',':'))+'\n')
    for company in companies(ROOT):
        texture('vandorlabs:blocks/access_reader/logos/' + company)
    manifest = dict(models=models, items=items, textures=list(textures.values()), faces=faces)
    (ASSETS / 'data').mkdir(parents=True, exist_ok=True)
    (ASSETS / 'data/reader_artwork.json').write_text(json.dumps(manifest, separators=(',', ':')) + '\n')
    print('Authored reader resources: ' + str(len(models)) + ' exact frame models, ' + str(len(textures)) + ' unchanged standard square sprites')
    return manifest


if __name__ == '__main__':
    generate()
