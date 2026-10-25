"""Check packaged controls against authored state, geometry, UV and item contracts."""
import json
from generate_control_resources import CONTROL_IDS


def validate(root, archive):
    assets = root / 'src/main/resources/assets/vandorlabs'
    checked = set()
    textures = set()

    def check_model(reference):
        if not reference.startswith('vandorlabs:'):
            return reference
        path = reference.split(':', 1)[1]
        if not path.startswith(('block/', 'item/')):
            path = 'block/' + path
        if path in checked:
            return 'vandorlabs:' + path
        checked.add(path)
        original = json.loads((assets / f'models/{path}.json').read_text())
        native = json.loads(archive.read(f'assets/vandorlabs/models/{path}.json'))
        # Every authored vertex/rotation/UV/display transform remains exact.
        expected = dict(original)
        if 'parent' in expected:
            expected['parent'] = check_model(expected['parent'])
        if 'textures' in expected:
            expected['textures'] = dict(expected['textures'])
            for key, value in original['textures'].items():
                if value.startswith('vandorlabs:blocks/'):
                    relative = value.removeprefix('vandorlabs:blocks/')
                    expected['textures'][key] = 'vandorlabs:block/' + relative
                    candidates = [root / f'texture-packs/{pack}/assets/vandorlabs/textures/blocks/{relative}.png'
                                  for pack in ('default', 'additional')] + [assets / f'textures/blocks/{relative}.png']
                    source = next(p for p in candidates if p.exists())
                    assert archive.read(f'assets/vandorlabs/textures/block/{relative}.png') == source.read_bytes(), ('control artwork drift', relative)
                    textures.add(relative)
        if 'overrides' in expected:
            expected['overrides'] = [dict(value, model=check_model(value['model'])) for value in original['overrides']]
        assert native == expected, ('control geometry/UV/item transform drift', path)
        return 'vandorlabs:' + path

    states = 0
    for family in CONTROL_IDS:
        original = json.loads((assets / f'blockstates/{family}.json').read_text())
        expected = {'variants': {state: dict(value, model=check_model(value['model']))
                                 for state, value in original['variants'].items()}}
        actual = json.loads(archive.read(f'assets/vandorlabs/blockstates/{family}.json'))
        assert actual == expected, ('control state/facing/rotation drift', family)
        states += len(expected['variants'])
        check_model('vandorlabs:item/' + family)
        recipe = assets / f'recipes/{family}.json'
        native_recipe = f'data/vandorlabs/recipe/{family}.json'
        if recipe.exists():
            expected_recipe = json.loads(recipe.read_text())
            expected_recipe['result']['id'] = expected_recipe['result'].pop('item')
            assert json.loads(archive.read(native_recipe)) == expected_recipe, ('control recipe drift', family)
        else:
            assert native_recipe not in archive.namelist(), ('hidden variant acquired recipe', family)
    print(f'Control resource contracts PASS: {states} authored states, {len(checked)} models and {len(textures)} exact textures')
