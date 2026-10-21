"""Append supplied Ancient Relief textures under Tech without changing saved picker IDs."""
import json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
folder=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/tech'
folder.mkdir(parents=True,exist_ok=True)
catalog=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
entries=json.loads(catalog.read_text(encoding='utf-8'))
for index in range(1,4):
    source=Image.open(ROOT/'build'/f'ancient relief ({index}).png').convert('RGBA')
    if source.size!=(128,128):source=source.resize((128,128),Image.Resampling.NEAREST)
    source.save(folder/f'ancient_relief_{index}.png',optimize=True)
    identifier=f'ancient_relief_{index}'
    if not any(e['id']==identifier for e in entries):
        entries.append(dict(id=identifier,label=f'Ancient Relief {index}',category='Tech',source=f'tech/{identifier}'))
catalog.write_text(json.dumps(entries,indent=2)+'\n',encoding='utf-8')
print('Imported three Ancient Relief textures at 128px under Tech; existing picker IDs retained')
