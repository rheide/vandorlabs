"""Import authored frame artwork at the established 128px frame resolution."""
import argparse
import json
import re
from pathlib import Path
from PIL import Image
from frame_edges import opaque_strip
ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('source', type=Path)
a = p.parse_args()
catalog = ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json'
entries = json.loads(catalog.read_text())
count = 0
for directory in ['frames-company-half-full', 'frames-door-window', 'frames-screen-full', 'frames-screen-half']:
    for source in sorted((a.source/directory).rglob('*.png')):
        relative = source.relative_to(a.source)
        stem = relative.with_suffix('').as_posix().replace('-', '_').replace('/', '_')
        identifier = 'frame_'+stem
        target = ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/frames'/relative
        target.parent.mkdir(parents=True, exist_ok=True)
        image = Image.open(source).convert('RGBA')
        aspect=image.width/image.height
        material=image.resize((128,max(1,round(128/aspect))),Image.Resampling.LANCZOS)
        canvas=Image.new("RGBA",(128,128));canvas.paste(material,(0,0));canvas.save(target,optimize=True)
        # A physical rail needs opaque edge material, never a full hollow-image fallback.
        opaque_strip(image).save(target.with_name(target.stem+'_rail.png'),optimize=True)
        if not any(e['id']==identifier for e in entries):
            entry = dict(id=identifier, label=source.stem.replace('_',' ').replace('-',' ').title(),
                         category='Frame', source='frames/'+relative.with_suffix('').as_posix(),
                         frame=True)
            if source.stem.startswith('lights-') or 'lightbar' in source.stem:
                entry['frameLight']=3
            entry["frameRail"]="frames/"+relative.with_suffix("").as_posix()+"_rail"
            entries.append(entry)
        for e in entries:
            if e["id"]==identifier:
                e["frameRail"]="frames/"+relative.with_suffix("").as_posix()+"_rail"
                for company,label in [('ashiro','Ashiro Mai'),('paladyne','Paladyne'),('pinnacle','Pinnacle'),('seyr','Seyr'),('vandor','Vandor'),('vektor','Vektor')]:
                    if company in source.stem.lower():
                        e['menuGroup']=label
                        break
                e["frameAspect"]=aspect
                e["frameBorder"]=image.getpixel((image.width//2,image.height//2))[3]<128
                e["label"]=re.sub(r"_1254(?:x(?:627|1254))?$", "",source.stem,flags=re.I).replace("_"," ").replace("-"," ").title()
                if directory in ('frames-screen-full','frames-screen-half'):
                    e['label'] += ' Half' if directory.endswith('-half') else ' Full'
        count += 1
catalog.write_text(json.dumps(entries,indent=2)+'\n')
print('Imported',count,'frame textures at 128px; existing choice indices preserved')
