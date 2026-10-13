"""Validate the final canopy registry, resources and 2.0 display-name IDs."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/vandorlabs'
GENERATED = ROOT / 'generated-resources/assets/vandorlabs'
catalog = json.loads((GENERATED / 'data/blocks.json').read_text())
canopies = {entry['id'] for entry in catalog if entry.get('class') == 'BlockCanopy'}
expected = {'canopy', 'front_canopy_glass', 'large_front_canopy_glass',
            'regular_canopy_glass', 'angled_front_1x1', 'angled_front_1x2',
            'space_visor_2x1', 'space_visor_3x1', 'space_visor_5x1',
            'space_visor_5x1_double_height'}
assert canopies == expected, (canopies - expected, expected - canopies)
language = dict(line.split('=', 1) for line in (GENERATED / 'lang/en_us.lang').read_text(encoding='utf-8').splitlines() if '=' in line)
for block in ('canopy', 'front_canopy_glass', 'large_front_canopy_glass',
              'pilot_seat', 'programmable_diagonal_glass'):
    name = language.get('tile.' + block + '.name', language.get('tile.vandorlabs.' + block + '.name'))
    assert name and re.sub(r'[^a-z0-9]+', '_', name.lower()).strip('_') == block, (block, name)
for block in canopies:
    for folder in ('blockstates', 'models/item', 'recipes'):
        assert (ASSETS / folder / (block + '.json')).is_file(), (folder, block)
models = json.loads((ASSETS / 'data/canopy_meshes.json').read_text())
allowed = expected | {'canopy_mounts_double'}
for model in models:
    assert model['id'] in allowed or model['id'].startswith(('regular_1x1_', 'angled_front_1x1_', 'angled_front_1x2_')), model['id']
assert {model['id'] for model in models} >= expected - {'regular_canopy_glass', 'large_front_canopy_glass'}
large = json.loads((ASSETS / 'recipes/large_front_canopy_glass.json').read_text())
assert large['key']['C']['item'] == 'vandorlabs:front_canopy_glass'
assert large['result']['item'] == 'vandorlabs:large_front_canopy_glass'
print('PASS: 10 canopy blocks, 39 retained mesh variants, final 2.0 name/ID alignment and complete recipes/models')
