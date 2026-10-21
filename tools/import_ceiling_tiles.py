"""Append the three ceiling finishes while preserving existing saved texture IDs."""
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
catalog = ROOT / 'generated-resources/assets/vandorlabs/data/unified_textures.json'
entries = json.loads(catalog.read_text(encoding='utf-8'))
for finish, label in [('pale_grey', 'Pale'), ('industrial_grey', 'Industrial'),
                      ('dark_cyan_metallic', 'Cyan')]:
    destination = ROOT / 'texture-packs/additional/assets/vandorlabs/textures/blocks/ceiling' / finish
    destination.mkdir(parents=True, exist_ok=True)
    for variant in ['plain', 'speaker', 'light_on', 'light_off']:
        image = Image.open(ROOT / 'build/ceiling_tiles_128' / finish / f'ceiling_{variant}.png').convert('RGBA')
        if image.size != (128, 128):
            image = image.resize((128, 128), Image.Resampling.NEAREST)
        image.save(destination / f'{variant}.png', optimize=True)
        identifier = f'ceiling_{finish}_{variant}'
        entry = dict(id=identifier, label=f'{label} '+('Speaker' if variant == 'speaker' else 'Ceiling'),
                     category='Lights' if variant.startswith('light_') else 'Panels',
                     source=f'ceiling/{finish}/{variant}')
        if variant == 'light_on':
            entry.update(lightOnly=True, unlit=f'ceiling/{finish}/light_off')
        elif variant == 'light_off':
            entry.update(label=f'{label} Ceiling Off', lightOnly=True, off=True)
        if not any(existing['id'] == identifier for existing in entries):
            entries.append(entry)
catalog.write_text(json.dumps(entries, indent=2)+'\n', encoding='utf-8')
print('Imported twelve 128px ceiling textures: six Panels and three paired Lights')
