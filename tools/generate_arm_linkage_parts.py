#!/usr/bin/env python3
"""Split the canonical authored linkage for independently posed bodies and elbow cover."""
import argparse
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/main/resources/assets/vandorlabs/models/block/gear'
NATIVE = ROOT / 'platforms/neoforge-1.21.1/src/main/resources/assets/vandorlabs/models/block/gear'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    canonical = json.loads((SOURCE / 'swing_linkage.json').read_text())
    for suffix, element in [('upper_body', 'upper_arm'), ('elbow', 'elbow_cover')]:
        part = copy.deepcopy(canonical)
        part['elements'] = [value for value in part['elements'] if value['name'] == element]
        if len(part['elements']) != 1:
            raise SystemExit('Canonical linkage must contain one ' + element)
        text = json.dumps(part, indent=2) + '\n'
        for destination in (SOURCE, NATIVE):
            path = destination / ('swing_linkage_' + suffix + '.json')
            if args.check:
                if not path.exists() or path.read_text() != text:
                    raise SystemExit('Stale generated linkage: ' + str(path.relative_to(ROOT)))
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text)
    print('Arm linkage parts match canonical artwork on both targets')

if __name__ == '__main__':
    main()
