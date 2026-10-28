"""Import authored frame artwork at the established 64px material resolution."""
import argparse
import json
import re
from pathlib import Path
from PIL import Image
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
        image.resize((64,64), Image.Resampling.LANCZOS).save(target, optimize=True)
        # Normalize an opaque border strip for narrow geometric rails. Transparent
        # center pixels must never become holes in the physical frame sides.
        alpha=image.getchannel('A')
        center=image.width//2
        start=0
        while start<image.height//3 and alpha.getpixel((center,start))<=64:
            start+=1
        end=start
        while end<image.height//3 and alpha.getpixel((center,end))>64:
            end+=1
        strip=image.crop((image.width//8,start,image.width*7//8,max(start+1,end))) if start<end<image.height//3 else image
        strip.resize((64,64),Image.Resampling.LANCZOS).save(target.with_name(target.stem+'_rail.png'),optimize=True)
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
                e["label"]=re.sub(r"_1254(?:x(?:627|1254))?$", "",source.stem,flags=re.I).replace("_"," ").replace("-"," ").title()
        count += 1
catalog.write_text(json.dumps(entries,indent=2)+'\n')
print('Imported',count,'frame textures at 64px; existing choice indices preserved')
