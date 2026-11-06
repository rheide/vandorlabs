#!/usr/bin/env python3
"""Project the current authored door model graph and standard full-height artwork."""
import hashlib
import json
from pathlib import Path
import shutil
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / 'build/generated/door-resources'
ASSETS = OUT / 'assets/vandorlabs'
MODEL_SOURCES = [ROOT / directory / 'assets/vandorlabs/models'
                 for directory in ('generated-resources', 'src/main/resources')]


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(',', ':')) + '\n')


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    catalog = json.loads((ROOT / 'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    doubles = {entry['source'] for entry in catalog if entry.get('category') == 'Double Doors'}
    textures, models, templates = {}, {}, {}

    def texture(name):
        if not name.startswith('vandorlabs:'):
            return name
        original = name.split(':', 1)[1]
        if original.startswith('blocks/space_doors/') and '/' not in original[len('blocks/space_doors/'):]:
            original = original.replace('blocks/space_doors/', 'blocks/space_doors/medium/', 1)
        target = 'door-artwork/' + original
        if target in textures:
            return 'vandorlabs:' + target
        canonical = original
        # The released standard resource pipeline aliases these low tiers to medium pixels.
        if canonical.startswith(tuple('blocks/' + family + '/low/' for family in
                                      ('space_doors', 'cargo_doors', 'glass_doors', 'programmable_glass'))):
            canonical = canonical.replace('/low/', '/medium/', 1)
        for directory in ('texture-packs/additional', 'texture-packs/default', 'src/main/resources'):
            source = ROOT / directory / 'assets/vandorlabs/textures' / (canonical + '.png')
            if source.is_file():
                break
        else:
            raise ValueError('Missing authored door artwork: ' + canonical)
        with Image.open(source) as image:
            image = image.convert('RGBA')
            span = 2 if canonical.removeprefix('blocks/') in doubles or image.height == 2 * image.width else 1
            limit = 128 * span
            if max(image.size) > limit:
                scale = limit / max(image.size)
                image = image.convert('RGBa').resize((round(image.width * scale), round(image.height * scale)), Image.Resampling.LANCZOS).convert('RGBA')
            width, height = image.size
            size = max(width, height)
            if size & (size - 1):
                raise ValueError('Non-power-of-two canonical door artwork: ' + canonical)
            padded = Image.new('RGBA', (size, size))
            padded.paste(image, (0, 0))
            if width < size:
                padded.paste(image.crop((width - 1, 0, width, height)).resize((size - width, height), Image.Resampling.NEAREST), (width, 0))
            if height < size:
                padded.paste(padded.crop((0, height - 1, size, height)).resize((size, size - height), Image.Resampling.NEAREST), (0, height))
            destination = ASSETS / 'textures' / (target + '.png')
            destination.parent.mkdir(parents=True, exist_ok=True)
            padded.save(destination, optimize=True)
            textures[target] = dict(id='vandorlabs:' + target, original='vandorlabs:' + original,
                                    width=width, height=height, size=size,
                                    usedU=width / size, usedV=height / size,
                                    alphaMips=original.endswith('_glass') or original.endswith('/glass_tile'))
        return 'vandorlabs:' + target

    def model(name):
        if ':' not in name:
            name = 'minecraft:' + name
        namespace, location = name.split(':', 1)
        if namespace != 'vandorlabs':
            return name
        if not location.startswith(('item/', 'block/')):
            location = 'block/' + location
        if location in models:
            return 'vandorlabs:' + location
        for directory in MODEL_SOURCES:
            source = directory / (location + '.json')
            if source.is_file():
                break
        else:
            raise ValueError('Missing authored door model: ' + location)
        value = json.loads(source.read_text())
        models[location] = value
        if 'parent' in value:
            value['parent'] = model(value['parent'])
        value['textures'] = {key: texture(item) for key, item in value.get('textures', {}).items()}
        if value.get('elements') and 'parent' not in value:
            geometry = {key: item for key, item in value.items() if key != 'textures'}
            encoded = json.dumps(geometry, sort_keys=True, separators=(',', ':'))
            key = hashlib.sha256(encoded.encode()).hexdigest()[:16]
            if key in templates and templates[key] != encoded:
                raise ValueError('Door geometry hash collision')
            if key not in templates:
                templates[key] = encoded
                write(ASSETS / 'models/block/door-shared' / (key + '.json'), geometry)
            value = dict(parent='vandorlabs:block/door-shared/' + key, textures=value['textures'])
        write(ASSETS / 'models' / (location + '.json'), value)
        return 'vandorlabs:' + location

    additional = []
    for file in sorted((MODEL_SOURCES[0] / 'item/detailed_doors/low').glob('*.json')):
        additional.append(model('vandorlabs:item/detailed_doors/low/' + file.stem))
    for name in ('programmable_door', 'large_programmable_door', 'space_rotating_door', 'space_sliding_door'):
        model('vandorlabs:item/' + name)
    texture('vandorlabs:blocks/control_buttons')
    previews = {}
    for entry in catalog:
        if ('design' in entry and entry.get('detail', 0) == 0) or entry.get('source', '').startswith('company_doors/'):
            previews[entry['id']] = texture('vandorlabs:blocks/' + entry['source'])
    def elements(name):
        location = name.split(':', 1)[1]
        value = models.get(location)
        if value is None:
            return []
        if 'elements' in value:
            return value['elements']
        return elements(value['parent']) if value.get('parent', '').startswith('vandorlabs:') else []
    faces = {name: sum(len(element.get('faces', {})) for element in elements(name)) for name in additional}
    artwork = {}
    for entry in catalog:
        source = entry.get('source', '')
        artwork[entry['id']] = dict(mirrored=source.startswith('company_doors/'),
                                  reversed=source.startswith(('company_doors/pinnacle/', 'company_doors/ashiro_mai/', 'company_doors/paladyne/', 'company_doors/vektor/')),
                                  light=source.startswith('company_doors/') and source.endswith('/light'))
    write(ASSETS / 'data/door_material_policy.json', artwork)
    write(ASSETS / 'data/door_artwork.json', dict(models=additional, textures=list(textures.values()), previews=previews, faces=faces))
    print('Authored door resources:', len(additional), 'additional models,', len(models), 'closure models,',
          len(templates), 'shared geometry templates,', len(textures), 'standard square-padded sprites; full content UV descriptors retained')


if __name__ == '__main__':
    main()
