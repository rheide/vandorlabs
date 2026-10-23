#!/usr/bin/env python3
"""Generate six active-face orientations for the seven Ramp Controller modes."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODES = ('ramp', 'filled_ramp', 'lift', 'extend', 'escalator_up', 'escalator_down', 'moat')
variants = {}
for active in (False, True):
    for mode, texture in enumerate(MODES):
        for face, rotation in {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}.items():
            variants[f'active={str(active).lower()},active_face={face},mode={mode}'] = {
                'model': f'vandorlabs:ramp_mode_{texture}_{"on" if active else "off"}', **rotation}
(ROOT/'generated-resources/assets/vandorlabs/blockstates/programmable_ramp.json').write_text(json.dumps({'variants': variants}, indent=2)+'\n')
