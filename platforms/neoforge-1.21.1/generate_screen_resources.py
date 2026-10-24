"""Transform canonical screen artwork; no independently maintained screen catalog."""
import json
import re
import shutil


def generate(root, resources):
    assets = resources / 'assets/vandorlabs'
    from PIL import Image
    white = assets / 'textures/gui/white.png'
    white.parent.mkdir(parents=True, exist_ok=True)
    Image.new('RGBA', (1, 1), (255, 255, 255, 255)).save(white)
    blocks = json.loads((root / 'generated-resources/assets/vandorlabs/data/blocks.json').read_text())
    by_id = {entry["id"]: entry for entry in blocks}
    authored_labels = {entry['source']: entry['label'] for entry in json.loads((root / 'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())}
    bare, framed = {}, {}
    for entry in blocks:
        if entry.get('type') != 'display':
            continue
        name = entry['id']
        key = re.sub(r'(^|_)(bare|framed)_', r'\1', name)
        target = bare if '_bare_' in name or name.startswith('bare_') else framed if '_framed_' in name or name.startswith('framed_') else None
        if target is None:
            bare[key] = framed[key] = name
        else:
            target[key] = name
    result = []
    for key in sorted(bare.keys() | framed.keys()):
        first, second = bare.get(key, framed.get(key)), framed.get(key, bare.get(key))
        for name in {first, second}:
            for suffix in ('_static.png', '_animated.anim'):
                source = root / f'texture-packs/default/assets/vandorlabs/textures/blocks/{name}{suffix}'
                if not source.exists():
                    source = root / f'src/main/resources/assets/vandorlabs/textures/blocks/{name}{suffix}'
                if source.exists():
                    destination = assets / f'textures/screens/{name}{suffix}'
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source, destination)
        result.append({'id': key, 'bare': first, 'framed': second, 'label': re.sub(r'\b(Bare|Framed)\s+', '', authored_labels[first + '_static']), 'bareHasFrame': by_id[first].get('framed', False), 'framedHasFrame': by_id[second].get('framed', False)})
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + '\n')
    write(assets / 'data/screen_artwork.json', result)
    input_sources = json.loads((root / 'generated-resources/assets/vandorlabs/data/input_surfaces.json').read_text())
    input_labels = {entry['source']: re.sub(r'\b(Bare|Framed)\s+', '', entry['label']) for entry in json.loads((root / 'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text()) if entry.get('category') == 'Screens'}
    input_bare, input_framed = {}, {}
    for entry in input_sources:
        name = entry['id']
        key = re.sub(r'(^|_)(bare|framed)_', r'\1', name)
        target = input_bare if '_bare_' in name or name.startswith('bare_') else input_framed if '_framed_' in name or name.startswith('framed_') else None
        if target is None:
            input_bare[key] = input_framed[key] = name
        else:
            target[key] = name
        for suffix in ('_static.png', '_off.png', '_animated.anim'):
            source = root / f'texture-packs/default/assets/vandorlabs/textures/blocks/console_inputs/{name}{suffix}'
            if source.exists():
                destination = assets / f'textures/screens/input/{name}{suffix}'
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
    inputs = []
    for key in sorted(input_bare.keys() | input_framed.keys()):
        first, second = input_bare.get(key, input_framed.get(key)), input_framed.get(key, input_bare.get(key))
        label = input_labels[f'console_inputs/{first}_static']
        inputs.append({'id': key, 'bare': first, 'framed': second, 'label': label})
    write(assets / 'data/input_artwork.json', {'defaultInput': input_sources[0]['id'], 'entries': inputs, 'frames': {entry['id']: entry['frames'] if entry['animated'] else 1 for entry in input_sources}})
    Image.new('RGB', (16, 16), (0, 0, 0)).save(assets / 'textures/screens/screen_off.png')
    shutil.copy2(root / 'texture-packs/default/assets/vandorlabs/textures/blocks/sequence_border_off.png', assets / 'textures/screens/sequence_border_off.png')

    rotations = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 90}, 'down': {'x': 270}}
    write(assets / 'blockstates/programmable_viewscreen.json', {'variants': {f'facing={face}': dict(model='vandorlabs:block/programmable_viewscreen', **rotation) for face, rotation in rotations.items()}})
    write(assets / 'models/block/programmable_viewscreen.json', {'textures': {'particle': 'vandorlabs:housing/dark_wall_panel'}, 'elements': []})
    write(assets / 'models/item/programmable_viewscreen.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'vandorlabs:housing/dark_wall_panel'}, 'display': {'gui': {'rotation': [30, 225, 0], 'scale': [.58, .58, .58]}}})
    write(resources / 'data/vandorlabs/loot_table/blocks/programmable_viewscreen.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'vandorlabs:programmable_viewscreen', 'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:block_entity_data']}]}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    families = ('programmable_console', 'programmable_half_console', 'programmable_input', 'programmable_half_input', 'programmable_diagonal_screen', 'programmable_diagonal_half_console')
    for family in families:
        # Keep the authored housing and GUI transforms, including the square-padded input artwork.
        model = json.loads((root / f'src/main/resources/assets/vandorlabs/models/item/{family}.json').read_text())
        model['parent'] = 'minecraft:block/block'
        for key, texture in model['textures'].items():
            namespace, path = texture.split(':', 1)
            if namespace != 'vandorlabs':
                raise ValueError(f'Unexpected screen item namespace: {texture}')
            relative = path.removeprefix('blocks/')
            source = root / f'texture-packs/default/assets/vandorlabs/textures/blocks/{relative}.png'
            if not source.exists():
                source = root / f'src/main/resources/assets/vandorlabs/textures/blocks/{relative}.png'
            if not source.exists():
                raise FileNotFoundError(source)
            destination = assets / f'textures/block/screen_items/{relative}.png'
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            model['textures'][key] = f'vandorlabs:block/screen_items/{relative}'
        write(assets / f'models/item/{family}.json', model)
        write(assets / f'blockstates/{family}.json', {'variants': {'': {'model': f'vandorlabs:block/{family}'}}})
        write(assets / f'models/block/{family}.json', {'textures': {'particle': 'vandorlabs:housing/dark_wall_panel'}, 'elements': []})
        write(resources / f'data/vandorlabs/loot_table/blocks/{family}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'vandorlabs:{family}', 'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:block_entity_data']}]}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    for tag in ('mineable/pickaxe', 'needs_stone_tool'):
        path = resources / f'data/minecraft/tags/block/{tag}.json'
        data = json.loads(path.read_text())
        data['values'] = list(dict.fromkeys(data['values'] + ['vandorlabs:' + family for family in families]))
        write(path, data)
    return len(result)
