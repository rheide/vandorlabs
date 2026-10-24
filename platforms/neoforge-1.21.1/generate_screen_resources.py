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
        result.append({'id': key, 'bare': first, 'framed': second, 'label': key.replace('_', ' ').title()})
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + '\n')
    write(assets / 'data/screen_artwork.json', result)
    rotations = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 90}, 'down': {'x': 270}}
    write(assets / 'blockstates/programmable_viewscreen.json', {'variants': {f'facing={face}': dict(model='vandorlabs:block/programmable_viewscreen', **rotation) for face, rotation in rotations.items()}})
    write(assets / 'models/block/programmable_viewscreen.json', {'textures': {'particle': 'vandorlabs:housing/dark_wall_panel'}, 'elements': []})
    write(assets / 'models/item/programmable_viewscreen.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'vandorlabs:housing/dark_wall_panel'}, 'display': {'gui': {'rotation': [30, 225, 0], 'scale': [.58, .58, .58]}}})
    write(resources / 'data/vandorlabs/loot_table/blocks/programmable_viewscreen.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'vandorlabs:programmable_viewscreen', 'functions': [{'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:block_entity_data']}]}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    language = assets / 'lang/en_us.json'
    if language.exists():
        lang = json.loads(language.read_text())
        lang['block.vandorlabs.programmable_viewscreen'] = 'Programmable Viewscreen'
        write(language, lang)
    return len(result)
