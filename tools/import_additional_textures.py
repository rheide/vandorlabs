#!/usr/bin/env python3
"""Bundle category folders without changing existing material identifiers or pixels."""
import json
import re
import shutil
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
pack = root / 'texture-packs/additional'
manifest = pack / 'catalog.json'
entries = json.loads(manifest.read_text()) if manifest.exists() else []
known = {entry['id']: entry for entry in entries}
source = Path(sys.argv[1]).expanduser()
seen = set()
for path in sorted(source.glob('*/*.png')):
    category = path.parent.name
    slug = re.sub(r'[^a-z0-9]+', '_', path.stem.lower()).strip('_')
    folder = re.sub(r'[^a-z0-9]+', '_', category.lower()).strip('_')
    identifier = 'imported_' + folder + '_' + slug
    assert identifier not in seen, 'Duplicate imported ID: ' + identifier
    seen.add(identifier)
    entry = dict(id=identifier, label=path.stem.replace('_', ' ').title(),
                 category=category.title(), source='imported/' + folder + '/' + slug)
    if identifier in known:
        assert known[identifier] == entry, 'Existing catalog metadata changed: ' + identifier
    else:
        entries.append(entry)
    target = pack / 'assets/vandorlabs/textures/blocks' / (entry['source'] + '.png')
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(path, target)
manifest.write_text(json.dumps(entries, indent=2) + '\n')
print('Bundled', len(seen), 'textures; catalog contains', len(entries), 'entries')
