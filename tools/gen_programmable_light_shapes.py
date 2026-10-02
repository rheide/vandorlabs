#!/usr/bin/env python3
"""Generate blockstate and item models for the two programmable light shapes."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assets = root / 'src/main/resources/assets/vandorlabs'
textures = ['porthole', 'light_column_wall', 'slatted_lamp', 'window_lamp', 'lightbar_wall', 'logo']
for name, bounds in [('programmable_light_frame', ([0, 0, 7.5], [16, 16, 8.5])),
                     ('programmable_light_slab', ([0, 4, 0], [16, 12, 16]))]:
    variants = {}
    halves = ['bottom', 'top'] if name.endswith('slab') else [None]
    for facing in ['north', 'south', 'east', 'west', 'up', 'down']:
        for half in halves:
            key = 'facing=' + facing + (',half=' + half if half else '')
            variants[key] = {'model': 'vandorlabs:' + name}
    (assets / 'blockstates' / (name + '.json')).write_text(json.dumps({'variants': variants}, indent=2) + '\n')
    (assets / 'models/block' / (name + '.json')).write_text(json.dumps({
        'textures': {'particle': 'vandorlabs:blocks/dark_wall_panel'}, 'elements': []}, indent=2) + '\n')
    (assets / 'models/item' / (name + '.json')).write_text(json.dumps({
        'parent': 'vandorlabs:item/configured/' + name + '_porthole_on'}, indent=2) + '\n')
    for texture in textures:
        for on in [False, True]:
            # These item models are the same dimensions as the actual housing.
            element = {'from': bounds[0], 'to': bounds[1], 'faces': {
                side: {'texture': '#face' if side in ['north', 'south'] else '#housing'}
                for side in ['north', 'south', 'east', 'west', 'up', 'down']}}
            model = {'parent': 'block/block', 'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.65, 0.65, 0.65]}}, 'textures': {
                'particle': 'vandorlabs:blocks/dark_wall_panel',
                'housing': 'vandorlabs:blocks/dark_wall_panel',
                'face': 'vandorlabs:blocks/' + texture + ('_on' if on else '_off')},
                'elements': [element]}
            path = assets / 'models/item/configured' / (name + '_' + texture + ('_on' if on else '_off') + '.json')
            path.write_text(json.dumps(model, indent=2) + '\n')
