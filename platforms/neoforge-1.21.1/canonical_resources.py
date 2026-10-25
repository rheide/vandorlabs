"""Canonical authored model dependencies translated to the native resource layout."""
import json
import shutil


class CanonicalResources:
    def __init__(self, root, resources):
        self.root, self.resources = root, resources
        self.source = root / 'src/main/resources/assets/vandorlabs'
        self.assets = resources / 'assets/vandorlabs'
        self.copied = set()

    @staticmethod
    def write(path, value):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value, indent=2) + '\n')

    def model_ref(self, value):
        # These vanilla parent names changed between the two supported targets.
        aliases = {'block/half_slab': 'minecraft:block/slab', 'block/upper_slab': 'minecraft:block/slab_top'}
        if value in aliases:
            return aliases[value]
        if not value.startswith('vandorlabs:'):
            return value
        path = value.split(':', 1)[1]
        if not path.startswith(('block/', 'item/')):
            path = 'block/' + path
        self.copy_model(path)
        return 'vandorlabs:' + path

    def texture(self, value):
        if not value.startswith('vandorlabs:blocks/'):
            return value
        relative = value.removeprefix('vandorlabs:blocks/')
        candidates = [self.root / f'texture-packs/{pack}/assets/vandorlabs/textures/blocks/{relative}.png'
                      for pack in ('default', 'additional')]
        candidates.append(self.source / f'textures/blocks/{relative}.png')
        source = next((path for path in candidates if path.exists()), None)
        if source is None:
            raise FileNotFoundError(value)
        target = self.assets / f'textures/block/{relative}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        if source.with_suffix('.png.mcmeta').exists():
            shutil.copy2(source.with_suffix('.png.mcmeta'), target.with_suffix('.png.mcmeta'))
        return 'vandorlabs:block/' + relative

    def translate(self, value):
        if isinstance(value, list):
            return [self.translate(child) for child in value]
        if not isinstance(value, dict):
            return value
        return {key: self.model_ref(child) if key in ('parent', 'model') and isinstance(child, str)
                else {name: self.texture(texture) for name, texture in child.items()} if key == 'textures'
                else self.translate(child) for key, child in value.items()}

    def copy_model(self, path):
        if path in self.copied:
            return
        self.copied.add(path)
        self.write(self.assets / f'models/{path}.json',
                   self.translate(json.loads((self.source / f'models/{path}.json').read_text())))

    def block(self, family):
        self.write(self.assets / f'blockstates/{family}.json',
                   self.translate(json.loads((self.source / f'blockstates/{family}.json').read_text())))
        self.copy_model('item/' + family)
        path = self.source / f'recipes/{family}.json'
        if path.exists():
            recipe = json.loads(path.read_text())
            recipe['result']['id'] = recipe['result'].pop('item')
            self.write(self.resources / f'data/vandorlabs/recipe/{family}.json', recipe)
