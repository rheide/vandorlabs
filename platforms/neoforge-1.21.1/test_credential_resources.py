#!/usr/bin/env python3
"""CPU packaging/geometry contract for exact canonical credential inventory artwork."""
import itertools
import json
import math
from pathlib import Path
import tempfile
from generate_credential_resources import companies, generate, translations
ROOT = Path(__file__).resolve().parents[2]


def main():
    with tempfile.TemporaryDirectory(prefix='credential-resources-') as temporary:
        out = Path(temporary)
        atlas = generate(ROOT, out)
        # Seven companies and the shared edge for both kinds, plus the four typed Data Chips.
        assert len(atlas) == 20 and len({entry['resource'] for entry in atlas}) == 20
        assert len(translations(ROOT)) == 14
        for kind in ('access_card', 'data_chip'):
            base = json.loads((out / 'assets/vandorlabs/models/item' / (kind + '.json')).read_text())
            company_overrides = [entry for entry in base['overrides'] if 'custom_model_data' in entry['predicate']]
            assert [entry['predicate']['custom_model_data'] for entry in company_overrides] == list(range(1, 8))
            typed = [entry for entry in base['overrides'] if 'vandorlabs:chip_type' in entry['predicate']]
            assert len(typed) == (4 if kind == 'data_chip' else 0) and len(typed) + 7 == len(base['overrides'])
            assert company_overrides[-1]['model'] == base['parent']
            for company in companies(ROOT):
                name = kind + '_' + company
                canonical = ROOT / 'src/main/resources/assets/vandorlabs/models/item' / (name + '.json')
                generated = out / 'assets/vandorlabs/models/item' / canonical.name
                assert canonical.read_bytes() == generated.read_bytes()
                model = json.loads(generated.read_text())
                gui = model['display']['gui']
                assert gui['rotation'] == [10, -15, 0] and gui['scale'] == [.85, .85, .85]
                for element in model['elements']:
                    for corner in itertools.product(*zip(element['from'], element['to'])):
                        x, y, z = [(corner[i] / 16 - .5) * gui['scale'][i] for i in range(3)]
                        ry, rx = map(math.radians, (-15, 10))
                        x, z = x * math.cos(ry) + z * math.sin(ry), z * math.cos(ry) - x * math.sin(ry)
                        y = y * math.cos(rx) - z * math.sin(rx)
                        assert -.44 < x < .44 and -.44 < y < .44, (name, corner)
        for source in atlas:
            relative = source['resource'].split(':')[1] + '.png'
            original = ROOT / 'texture-packs/additional/assets/vandorlabs/textures' / relative
            assert original.read_bytes() == (out / 'assets/vandorlabs/textures' / relative).read_bytes()
        assert not (out / 'data').exists(), 'No invented credential recipes'
    print('Credential resources PASS14exactmodels/20exacttextures/14translations, native overrides and all rotated GUI corners padded')


if __name__ == '__main__':
    main()
