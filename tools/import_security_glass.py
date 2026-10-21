"""Import four supplied glass patterns, removing their baked preview checkerboard."""
import json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
folder=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/security_glass'
folder.mkdir(parents=True,exist_ok=True)
catalog=ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
entries=json.loads(catalog.read_text(encoding='utf-8'))
for index in range(1,5):
    source=Image.open(ROOT/'build'/f'security glass ({index}).png').convert('RGB')
    image=Image.new('RGBA',source.size)
    pixels=[]
    for y in range(source.height):
        for x in range(source.width):
            color=source.getpixel((x,y))
            if max(color)-min(color)<=4 and min(color)>=195:
                pixels.append((190,218,230,0));continue
            # The authored preview uses alternating 24px grey squares.
            background=231 if (x//24+y//24)%2==0 else 211
            alpha=.72 if min(color)<180 else .45
            rgb=tuple(max(0,min(255,round((c-background*(1-alpha))/alpha))) for c in color)
            pixels.append((*rgb,round(alpha*255)))
    image.putdata(pixels)
    image.resize((128,128),Image.Resampling.NEAREST).save(folder/f'security_{index}.png')
    identifier=f'security_glass_{index}'
    if not any(e['id']==identifier for e in entries):
        entries.append(dict(id=identifier,label=f'Security {index}',category='Glass',source=f'security_glass/security_{index}'))
catalog.write_text(json.dumps(entries,indent=2)+'\n',encoding='utf-8')
print('Imported four 128px security glass patterns with transparent backgrounds and stable appended picker IDs')
