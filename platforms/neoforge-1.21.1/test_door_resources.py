#!/usr/bin/env python3
"""Check full authored door inheritance/materials and frozen actual standard Forge pixels."""
import hashlib
import json
from pathlib import Path
from PIL import Image
from generate_door_resources import ROOT, OUT, ASSETS, MODEL_SOURCES


def main():
    data = json.loads((ASSETS / 'data/door_artwork.json').read_text())
    reference = json.loads((Path(__file__).parent / 'reference/door-artwork-pixels.json').read_text())
    by_id = {entry['id']: entry for entry in data['textures']}
    def resolve(name, native):
        namespace, relative = name.split(':', 1)
        if namespace != 'vandorlabs':
            return {}
        if not relative.startswith(('block/', 'item/')):
            relative = 'block/' + relative
        directories = [ASSETS / 'models'] if native else MODEL_SOURCES
        file = next((directory / (relative + '.json') for directory in directories
                     if (directory / (relative + '.json')).is_file()), None)
        if file is None:
            raise AssertionError('Missing model dependency ' + name)
        value = json.loads(file.read_text())
        result = resolve(value['parent'], native) if 'parent' in value else {}
        old_textures = dict(result.get('textures', {}))
        result.update({key: item for key, item in value.items() if key != 'parent'})
        old_textures.update(value.get('textures', {}))
        result['textures'] = old_textures
        return result
    models = 0
    for name in data['models']:
        source, target = resolve(name, False), resolve(name, True)
        for key, value in target['textures'].items():
            if value.startswith('vandorlabs:'):
                target['textures'][key] = by_id[value]['original']
        for key, value in source['textures'].items():
            prefix = 'vandorlabs:blocks/space_doors/'
            if value.startswith(prefix) and '/' not in value[len(prefix):]:
                source['textures'][key] = prefix + 'medium/' + value[len(prefix):]
        if source != target:
            raise AssertionError('Authored resolved geometry/materials changed ' + name)
        models += 1
    for entry in data['textures']:
        image = Image.open(ASSETS / ('textures/' + entry['id'].split(':', 1)[1] + '.png')).convert('RGBA')
        if image.size != (entry['size'], entry['size']) or image.width & (image.width - 1):
            raise AssertionError('Invalid native square sprite ' + entry['id'])
        content = image.crop((0, 0, entry['width'], entry['height']))
        expected = reference[entry['original']]
        if content.size != (expected['width'], expected['height']) or hashlib.sha256(content.tobytes()).hexdigest() != expected['rgbaSha256']:
            raise AssertionError('Actual Forge content pixels changed ' + entry['original'])
        if entry['usedU'] != content.width / image.width or entry['usedV'] != content.height / image.height:
            raise AssertionError('Lost full-artwork content UV ratio ' + entry['id'])
        for x, y in [(image.width-1, 0), (0, image.height-1), (image.width-1, image.height-1)]:
            if image.getpixel((x, y)) != content.getpixel((min(x, content.width-1), min(y, content.height-1))):
                raise AssertionError('Door square padding must extend edge texels ' + entry['id'])
    if models != 1924 or len(data['textures']) != 68 or set(reference) != {item['original'] for item in data['textures']}:
        raise AssertionError('Incomplete current authored door resource inventory')
    print('Authored door resource projection PASS models=' + str(models) + ' sprites=68 actual standard Forge RGBA, exact inherited geometry/materials and padded content ratios; runtime baking separate')


if __name__ == '__main__':
    main()
