#!/usr/bin/env python3
"""Give tile-aware map adapters the complete bundled programmable material palette."""
import json
import re
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/vandorlabs'
MARKER='# Generated tile-aware material palette\n'
BLOCKS={
    'programmable_slab':'ProgrammableSlabRenderer',
    'programmable_stairs':'ProgrammableShapeRenderer',
    'programmable_block':'ProgrammableShapeRenderer',
    'programmable_wall':'ProgrammableShapeRenderer',
    'programmable_diagonal_wall':'ProgrammableShapeRenderer',
    'programmable_trapdoor':'ProgrammableTrapdoorRenderer',
    'programmable_diagonal_trapdoor':'ProgrammableDiagonalTrapdoorRenderer',
    'programmable_door':'ProgrammableDoorRenderer',
    'large_programmable_door':'LargeProgrammableDoorRenderer',
}

def palette(full=False,check=False):
    legacy=re.findall(r'new Finish\("[^"]+", "([^"]+)"\)',(ROOT/'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text())
    extras=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    menu=json.loads((ASSETS/'data/texture_menu.json').read_text())
    ids=re.findall(r'new Finish\("([^"]+)", "[^"]+"\)',(ROOT/'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text())
    textures=[path if not menu.get(name,{}).get('hidden') else legacy[0] for name,path in zip(ids,legacy)]
    for entry in extras:
        # Redirected/retired choices never emit their original palette slot.
        redirected=entry.get('alias') or entry.get('detail',0)>0
        if entry.get('hidden') or redirected:
            textures.append(legacy[0]);continue
        path=entry['source']
        # These equal-pixel small derivatives are generated during JAR staging.
        if path.startswith('imported/trapdoors/') and path.endswith('_small'):path=path[:-6]
        if 'rectangular' in entry or 'top' in entry:
            relative=Path(path+'.png')
            source=next((folder/relative for folder in (ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks',ROOT/'texture-packs/default/assets/vandorlabs/textures/blocks') if (folder/relative).exists()),None)
            if source is None:raise FileNotFoundError(path)
            image=Image.open(source).convert('RGBA')
            crop='crop' in entry
            x0=image.width//16 if crop else 0;y0=image.height//32 if crop else 0
            w=image.width-x0;h=image.height-2*y0
            if not full and 'design' in entry and entry.get('category')!='Double Doors':h//=2;y0+=h
            image=image.crop((x0,y0,x0+w,y0+h))
            image.thumbnail((128,128),Image.Resampling.LANCZOS)
            path='dynmap/'+entry['id']+('_full' if full and 'design' in entry else '')
            target=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks'/(path+'.png')
            if check:
                with Image.open(target) as actual:
                    if actual.size!=image.size or actual.convert('RGBA').tobytes()!=image.tobytes():raise AssertionError('Stale Dynmap crop: '+path)
            else:
                target.parent.mkdir(parents=True,exist_ok=True);image.save(target,optimize=True)
        textures.append(path)
    return textures

def generate(check=False):
    paths=palette(check=check)+palette(True,check=check)+['space_doors/medium/double_frame_metal','space_doors/medium/hinge','programmable_glass/metal_side']
    catalog=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/blocks.json').read_text())
    omitted={entry['id'] for entry in catalog if entry.get('internal_model')}
    registered={entry['id'] for entry in catalog if not entry.get('retired') and not entry.get('internal_model') and not entry.get('programmable_only')}
    finishes=set(re.findall(r'new Finish\("([^"]+)",',(ROOT/'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text()))
    omitted.update(finishes-registered)
    for filename in ('dynmap-models.txt','dynmap-texture.txt'):
        path=ASSETS/filename
        old=path.read_text().split(MARKER)[0].splitlines()
        old=[line for line in old if not any(line.startswith(prefix+'id=%'+block+',') for prefix in ('customblock:','modellist:','block:') for block in BLOCKS)]
        old=[line for line in old if not (match:=re.match(r'(?:modellist|customblock|block):id=%([^,]+),',line)) or match[1] not in omitted]
        if filename=='dynmap-models.txt':
            new=[f'customblock:id=%{block},state=*,class=com.vandorlabs.dynmap.{renderer}' for block,renderer in BLOCKS.items()]
        else:
            new=[f'texture:id=housing_{i},filename=assets/vandorlabs/textures/blocks/{texture}.png,xcount=1,ycount=1' for i,texture in enumerate(paths)]
            mapping=''.join(f',patch{i}=0:housing_{i}' for i in range(len(paths)))
            new += [f'block:id=%{block},state=*'+mapping+',transparency=SEMITRANSPARENT' for block in BLOCKS]
        content='\n'.join(old).rstrip()+'\n\n'+MARKER+'\n'.join(new)+'\n'
        if check:
            if path.read_text()!=content:raise AssertionError('Stale Dynmap definitions: '+filename)
        else:path.write_text(content)
    print(f'Dynmap material palette: {len(paths)} choices, {len(BLOCKS)} adapters')

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true')
    generate(parser.parse_args().check)
