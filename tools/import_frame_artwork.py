"""Import authored frame artwork at the established 64px material resolution."""
import argparse
import json
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
        run=0
        while run<image.height//3 and alpha.getpixel((center,run))>64:
            run+=1
        strip=image.crop((0,0,image.width,max(1,run))) if 0<run<image.height//3 else image
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
            if e["id"]==identifier:e["frameRail"]="frames/"+relative.with_suffix("").as_posix()+"_rail"
        count += 1
catalog.write_text(json.dumps(entries,indent=2)+'\n')
print('Imported',count,'frame textures at 64px; existing choice indices preserved')
