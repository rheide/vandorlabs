#!/usr/bin/env python3
"""Import optional turntable surface artwork and append stable shared material choices."""
import argparse
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/turntable'
DESIGNS = [
    ('Vanilla Stone-Iron Rotary Disk-1.png', 'stone_iron', 'Stone and Iron'),
    ('Pixelated sci-fi rotary deck-2.png', 'rotary_deck', 'Rotary Deck'),
    ('Embossed basalt octagonal turntable top-3.png', 'basalt', 'Embossed Basalt'),
    ('Graphite and ivory sci-fi turntable-4.png', 'graphite_ivory', 'Graphite and Ivory'),
]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    args = parser.parse_args()
    entries = [dict(id='turntable_original_' + state, label='Original ' + state.title(),
                    category='Turntable', source='turntable/turntable_block_top' + suffix)
               for state, suffix in [('off', ''), ('on', '_on')]]
    for filename, slug, label in DESIGNS:
        with Image.open(args.source / filename) as image:
            if image.width != image.height:
                raise ValueError('Surface artwork must be square: ' + filename)
            for size, suffix in [(512, ''), (64, '_low')]:
                image.convert('RGBA').resize((size, size), Image.Resampling.LANCZOS).save(
                    ART / ('turntable_block_top_' + slug + suffix + '.png'))
        entries.append(dict(id='turntable_' + slug, label=label, category='Turntable',
                            source='turntable/turntable_block_top_' + slug))
    (ROOT / 'texture-packs/additional/turntable-catalog.json').write_text(
        json.dumps(entries, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
