"""Translate canonical mounted-control models, artwork and available recipes."""
import json
import shutil

CONTROL_IDS = (
    'push_button', 'rocker_switch',
    'industrial_power_lever', 'compact_power_lever',
    'small_power_lever', 'large_power_lever',
    'thruster_lever', 'airliner_throttle', 'fighter_throttle', 'wall_slider',
)


def generate(root, resources):
    source = root / 'src/main/resources/assets/vandorlabs'
    assets = resources / 'assets/vandorlabs'
    copied = set()

    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, indent=2) + '\n')

    def model_ref(value):
        if not value.startswith('vandorlabs:'):
            return value
        path = value.split(':', 1)[1]
        if not path.startswith(('block/', 'item/')):
            path = 'block/' + path
        copy_model(path)
        return 'vandorlabs:' + path

    def copy_texture(value):
        if not value.startswith('vandorlabs:blocks/'):
            return value
        relative = value.removeprefix('vandorlabs:blocks/')
        candidates = [root / f'texture-packs/{pack}/assets/vandorlabs/textures/blocks/{relative}.png'
                      for pack in ('default', 'additional')]
        candidates.append(source / f'textures/blocks/{relative}.png')
        texture = next((path for path in candidates if path.exists()), None)
        if texture is None:
            raise FileNotFoundError(value)
        target = assets / f'textures/block/{relative}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(texture, target)
        metadata = texture.with_suffix('.png.mcmeta')
        if metadata.exists():
            shutil.copy2(metadata, target.with_suffix('.png.mcmeta'))
        return 'vandorlabs:block/' + relative

    def translate(value):
        if isinstance(value, list):
            return [translate(child) for child in value]
        if not isinstance(value, dict):
            return value
        result = {}
        for key, child in value.items():
            if key in ('model', 'parent') and isinstance(child, str):
                result[key] = model_ref(child)
            elif key == 'textures':
                result[key] = {name: copy_texture(texture) for name, texture in child.items()}
            else:
                result[key] = translate(child)
        return result

    def copy_model(path):
        if path in copied:
            return
        copied.add(path)
        value = json.loads((source / f'models/{path}.json').read_text())
        write(assets / f'models/{path}.json', translate(value))

    for family in CONTROL_IDS:
        states = json.loads((source / f'blockstates/{family}.json').read_text())
        write(assets / f'blockstates/{family}.json', translate(states))
        copy_model('item/' + family)
        recipe_path = source / f'recipes/{family}.json'
        if recipe_path.exists():
            recipe = json.loads(recipe_path.read_text())
            recipe['result']['id'] = recipe['result'].pop('item')
            write(resources / f'data/vandorlabs/recipe/{family}.json', recipe)
    for tag in ('mineable/pickaxe', 'needs_stone_tool'):
        path = resources / f'data/minecraft/tags/block/{tag}.json'
        data = json.loads(path.read_text())
        data['values'] = list(dict.fromkeys(data['values'] + ['vandorlabs:' + family for family in CONTROL_IDS if 'power_lever' not in family]))
        write(path, data)
