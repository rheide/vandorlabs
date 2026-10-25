#!/usr/bin/env python3
"""Stage 128 pixels per block and compact inherited geometry without changing IDs."""
import argparse, collections, hashlib, json, shutil
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]

def texture_aliases(catalog):
    aliases={'vandorlabs:blocks/unified/'+e['id']:'vandorlabs:blocks/unified/'+e['alias'] for e in catalog if e.get('alias')}
    for tier in ('low','medium'):
        for suffix in ('_mild',):
            aliases['vandorlabs:blocks/programmable_glass/'+tier+'/clear'+suffix]='vandorlabs:blocks/programmable_glass/'+tier+'/clear'
    return aliases

def resized(path,size):
    with Image.open(path) as source:
        image=source.convert('RGBA').convert('RGBa').resize((size,size),Image.Resampling.LANCZOS).convert('RGBA')
    image.save(path,optimize=True)

def standardize(textures,catalog):
    """Keep UV atlases/native pixel art; use 128px per authored block face."""
    # OBJ materials address packed UV regions. Their atlas size is not a
    # per-block artwork resolution, even when the filename omits "atlas".
    obj_atlases=set()
    for material in (textures.parent/'models').rglob('*.mtl'):
        for line in material.read_text().splitlines():
            if line.startswith('map_Kd vandorlabs:blocks/'):
                obj_atlases.add(line.split('vandorlabs:blocks/',1)[1].strip()+'.png')
    doubles={e['source'] for e in catalog if e.get('category')=='Double Doors'}
    changed=0
    for path in (textures/'blocks').rglob('*.png'):
        rel=path.relative_to(textures/'blocks').as_posix()
        metadata=path.with_suffix('.png.mcmeta')
        animated=metadata.exists() and 'animation' in json.loads(metadata.read_text())
        # Programmable Surface masters span complete pads; their 64px local variants remain native too.
        if rel in obj_atlases or 'atlas' in path.stem or rel.startswith(('canopy/','aircraft_throttles/','universal_constructor/','turntable/turntable_block_top','surface/')) or animated:continue
        with Image.open(path) as source:
            w,h=source.size
            # Tall doors cover two blocks; square double doors cover four.
            span=2 if rel[:-4] in doubles or h==2*w else 1
            limit=128*span
            if max(w,h)<=limit:continue
            scale=limit/max(w,h);size=(round(w*scale),round(h*scale))
            image=source.convert('RGBA').convert('RGBa').resize(size,Image.Resampling.LANCZOS).convert('RGBA')
        image.save(path,optimize=True);changed+=1
    # The old detail paths remain resource-pack aliases with identical pixels.
    for family in ('space_doors','cargo_doors','glass_doors','programmable_glass'):
        for master in (textures/'blocks'/family/'medium').glob('*.png'):
            low=master.parent.parent/'low'/master.name
            if low.exists():shutil.copyfile(master,low)
    print('Standard artwork: 128 pixels/block;',changed,'images reduced; UV atlases and animation sheets preserved')

def redirect_dynmap(text,aliases):
    import re
    text=text.replace('blocks/space_doors/high/','blocks/space_doors/medium/').replace('blocks/cargo_doors/high/','blocks/cargo_doors/medium/')
    text=re.sub(r'(filename=(?:assets/vandorlabs/textures/)?blocks/space_doors/)([^/\s,]+\.png)',r'\1medium/\2',text)
    for old,new in aliases.items():text=text.replace(old.split(':',1)[1]+'.png',new.split(':',1)[1]+'.png')
    return text

def redirect(value,texture_aliases):
    if isinstance(value,str):
        value=value.replace('detailed_doors/high/','detailed_doors/medium/').replace('item/high/','item/medium/').replace('block/high/','block/medium/')
        value=value.replace('blocks/space_doors/high/','blocks/space_doors/medium/').replace('blocks/cargo_doors/high/','blocks/cargo_doors/medium/')
        prefix='vandorlabs:blocks/space_doors/'
        if value.startswith(prefix) and '/' not in value[len(prefix):]:value=prefix+'medium/'+value[len(prefix):]
        for old,new in texture_aliases.items():
            if value==old or value==old+'_half':return new+('_half' if value.endswith('_half') else '')
        return value
    if isinstance(value,list):return [redirect(v,texture_aliases) for v in value]
    if isinstance(value,dict):return {k:redirect(v,texture_aliases) for k,v in value.items()}
    return value

def stage(directory, models_staged=False):
    textures=directory/'textures';models=directory/'models'
    if not models_staged:
        shutil.rmtree(models,ignore_errors=True)
        for source in [ROOT/'src/main/resources/assets/vandorlabs/models',ROOT/'generated-resources/assets/vandorlabs/models']:
            shutil.copytree(source,models,dirs_exist_ok=True)
    before_models=list(models.rglob('*.json'))
    before_bytes=sum(p.stat().st_size for p in before_models)
    # Drop actual old High artwork and legacy root copies; redirect references first.
    for family in ['space_doors','cargo_doors']:
        shutil.rmtree(textures/'blocks'/family/'high',ignore_errors=True)
    for file in (textures/'blocks/space_doors').glob('*'):
        if file.is_file():file.unlink()
    for file in (textures/'items').glob('*.png'):resized(file,128)
    # Remove derivatives from earlier staging runs before reading imported masters.
    for file in (textures/'blocks/imported/trapdoors').glob('*_small.png'):file.unlink()
    for file in (textures/'blocks/imported/trapdoors').glob('*.png'):
        small=file.with_name(file.stem+'_small.png')
        shutil.copyfile(file,small);resized(small,128);resized(file,256)
    # All pane sizes use the same reflected pattern, at different native resolutions.
    pane=textures/'blocks/space_doors/medium/glass_tile.png'
    low=textures/'blocks/space_doors/low/glass_tile.png'
    shutil.copyfile(pane,low);resized(low,256)
    catalog=json.loads((directory/'data/unified_textures.json').read_text())
    standardize(textures,catalog)
    aliases=texture_aliases(catalog)
    for tier in ('low','medium'):
        for suffix in ('_mild',):
            duplicate=textures/'blocks/programmable_glass'/tier/('clear'+suffix+'.png')
            if duplicate.exists():duplicate.unlink()
    removed=0
    templates={};reused=0
    shared=models/'block/runtime_shared';shared.mkdir(parents=True,exist_ok=True)
    for file in before_models:
        if 'high' in file.relative_to(models).parts:
            file.unlink();removed+=1;continue
        model=redirect(json.loads(file.read_text()),aliases)
        if model.get('elements') and 'parent' not in model and shared not in file.parents:
            geometry={k:v for k,v in model.items() if k!='textures'}
            encoded=json.dumps(geometry,sort_keys=True,separators=(',',':'))
            name=hashlib.sha256(encoded.encode()).hexdigest()[:16]
            if name not in templates:
                (shared/(name+'.json')).write_text(encoded+'\n');templates[name]=encoded
            elif templates[name]!=encoded:raise AssertionError('Geometry hash collision')
            model=dict(parent='vandorlabs:block/runtime_shared/'+name,textures=model.get('textures',{}))
            reused+=1
        file.write_text(json.dumps(model,separators=(',',':'))+'\n')
    for path in sorted(models.rglob('high'),reverse=True):
        if path.is_dir():shutil.rmtree(path)
    for file in (directory/'blockstates').glob('*.json'):
        file.write_text(json.dumps(redirect(json.loads(file.read_text()),aliases),separators=(',',':'))+'\n')
    for file in directory.glob('dynmap*.txt'):
        file.write_text(redirect_dynmap(file.read_text(),aliases))
    after_models=list(models.rglob('*.json'))
    print('Runtime assets: removed',removed,'High models;',reused,'geometry models share',len(templates),'templates;',len(after_models),'models;',before_bytes,'->',sum(p.stat().st_size for p in after_models),'JSON bytes')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage',type=Path,required=True)
    parser.add_argument('--models-staged',action='store_true',help='Use models already copied by resource processing')
    args=parser.parse_args();stage(args.stage,args.models_staged)
