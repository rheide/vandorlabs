#!/usr/bin/env python3
"""Check moving-arm clearance and throw geometry for every supplied lever pose."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
models = root / 'src/main/resources/assets/vandorlabs/models/block/twin_power_levers'
count = 0
for path in sorted(models.glob('*.json')):
    elements = json.loads(path.read_text())['elements']
    small = path.name.startswith('small_')
    on = '_on_' in path.name
    for arm, cap in ((7, 2), (10, 5)):
        pivot_axis = 'xyz'.index(elements[arm]['rotation']['axis'])
        for index in range(arm, arm + 3):
            part = elements[index]
            assert part['from'][pivot_axis] >= elements[cap]['from'][pivot_axis] + .0625, (path, index, 'coplanar pivot face')
            assert part['to'][pivot_axis] <= elements[cap]['to'][pivot_axis] - .0625, (path, index, 'coplanar pivot face')
    for part in elements[7:16]:
        assert abs(part['rotation']['angle']) == (22.5 if small and not on else 45), (path, 'throw angle')
    lower = elements[7]
    lengths = [b-a for a, b in zip(lower['from'], lower['to'])]
    assert max(lengths) == 2.5, (path, 'shortened lower arm length')
    count += 1
assert count == 24, count
print(f'Twin power lever geometry PASS: {count} poses, recessed arm faces, shortened arms in both sizes and increased Small On angle')
