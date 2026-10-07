"""Read the bundled material choices used by the live catalog gallery."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def choices():
    source = (ROOT / 'src/main/java/com/vandorlabs/tiles/ScreenHousingTextures.java').read_text()
    original = re.findall(r'new Finish\("([^"]+)",\s*"[^"]+"\)', source)
    menu = json.loads((ROOT / 'src/main/resources/assets/vandorlabs/data/texture_menu.json').read_text())
    extra = json.loads((ROOT / 'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    lang = dict(line.split('=', 1) for line in (ROOT / 'generated-resources/assets/vandorlabs/lang/en_us.lang').read_text().splitlines() if '=' in line)
    entries = []
    for index, entry in enumerate([dict(menu.get(name, {}), id=name) for name in original] + extra):
        category = entry.get('category', 'Materials')
        if entry.get('hidden') or category in ('Screens', 'Lights', 'Doors', 'Double Doors'):
            continue
        label = entry.get('label', lang.get('tile.vandorlabs.' + entry['id'] + '.name', entry['id']))
        entries.append(dict(choice=index, label=label, category=category))
    return sorted(entries, key=lambda entry: (entry['category'].lower(), entry['label'].lower(), entry['choice']))
