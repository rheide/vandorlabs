"""Generate optional static Dynmap texture mappings from the native cube models."""
import json

DIRECTIONS = ('down', 'up', 'north', 'east', 'south', 'west')
HORIZONTAL = DIRECTIONS[2:]
DYNMAP_FACES = {'down': 'bottom', 'up': 'top', **{side: side for side in HORIZONTAL}}


def generate(out):
    assets = out / 'assets/vandorlabs'
    textures = {}
    blocks = []

    def texture_id(resource):
        namespace, path = resource.split(':', 1)
        file = out / f'assets/{namespace}/textures/{path}.png'
        if not file.is_file():
            raise ValueError(f'Dynmap texture missing from generated native resources: {resource}')
        return textures.setdefault(file.relative_to(out).as_posix(), f'vl_surface_{len(textures)}')

    for block in ('turntable_surface', 'copy_controller'):
        states = json.loads((assets / f'blockstates/{block}.json').read_text())['variants']
        faces = DIRECTIONS if block == 'turntable_surface' else HORIZONTAL
        for facing in faces:
            variant = next(value for key, value in states.items() if f'facing={facing}' in key.split(','))
            model_path = variant['model'].removeprefix('vandorlabs:')
            model = json.loads((assets / f'models/{model_path}.json').read_text())
            element = model['elements'][0]
            if (element['from'], element['to']) != ([0, 0, 0], [16, 16, 16]):
                raise ValueError(f'Dynmap fallback requires a cubic housing: {model_path}')
            face_textures = {side: {part['faces'][side]['texture'] for part in model['elements'] if side in part['faces']}
                             for side in DIRECTIONS}
            if any(len(values) != 1 for values in face_textures.values()):
                raise ValueError(f'Dynmap fallback requires one texture per side: {model_path}')
            turns = variant.get('y', 0) // 90
            slots = list(DIRECTIONS[:2]) + list(HORIZONTAL[turns:] + HORIZONTAL[:turns])
            mapping = []
            for local, world in zip(DIRECTIONS, slots):
                key = next(iter(face_textures[local])).removeprefix('#')
                mapping.append(f'{DYNMAP_FACES[world]}=0:{texture_id(model["textures"][key])}')
            blocks.append(f'block:id=%{block},state=facing:{facing},' + ','.join(mapping) + ',stdrot=true')

    rows = ['modname:vandorlabs', '# Static default artwork for native Surface and controller cubes.']
    rows.extend(f'texture:id={name},filename={path},xcount=1,ycount=1' for path, name in textures.items())
    rows.extend(blocks)
    (assets / 'dynmap-texture.txt').write_text('\n'.join(rows) + '\n', encoding='utf-8')
