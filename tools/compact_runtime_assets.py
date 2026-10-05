#!/usr/bin/env python3
"""Stage two artwork tiers and compact inherited model geometry without changing IDs."""
import argparse, collections, hashlib, json, shutil
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]

def resized(path,size):
    with Image.open(path) as source:
        image=source.convert('RGBA').convert('RGBa').resize((size,size),Image.Resampling.LANCZOS).convert('RGBA')
    image.save(path,optimize=True)

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

def stage(directory):
    textures=directory/'textures';models=directory/'models'
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
    aliases={}
    for entry in catalog:
        if entry.get('alias'):
            aliases['vandorlabs:blocks/unified/'+entry['id']]='vandorlabs:blocks/unified/'+entry['alias']
    removed=0
    for file in before_models:
        if 'high' in file.relative_to(models).parts:
            file.unlink();removed+=1;continue
        model=redirect(json.loads(file.read_text()),aliases)
        file.write_text(json.dumps(model,separators=(',',':'))+'\n')
    for path in sorted(models.rglob('high'),reverse=True):
        if path.is_dir():shutil.rmtree(path)
    for file in (directory/'blockstates').glob('*.json'):
        file.write_text(json.dumps(redirect(json.loads(file.read_text()),aliases),separators=(',',':'))+'\n')
    for file in directory.glob('dynmap*.txt'):
        text=file.read_text().replace('blocks/space_doors/high/','blocks/space_doors/medium/').replace('blocks/cargo_doors/high/','blocks/cargo_doors/medium/')
        import re
        text=re.sub(r'filename=blocks/space_doors/([^/\s,]+\.png)',r'filename=blocks/space_doors/medium/\1',text)
        file.write_text(text)
    # Existing asset names remain small children of shared geometry templates.
    templates={};reused=0
    for file in list(models.rglob('*.json')):
        model=json.loads(file.read_text())
        if model.get('elements') and 'parent' not in model:
            geometry={k:v for k,v in model.items() if k!='textures'}
            encoded=json.dumps(geometry,sort_keys=True,separators=(',',':'))
            name=hashlib.sha256(encoded.encode()).hexdigest()[:16]
            if name not in templates:
                target=models/'block/runtime_shared'/(name+'.json');target.parent.mkdir(parents=True,exist_ok=True)
                target.write_text(encoded+'\n');templates[name]=encoded
            elif templates[name]!=encoded:raise AssertionError('Geometry hash collision')
            file.write_text(json.dumps(dict(parent='vandorlabs:block/runtime_shared/'+name,textures=model.get('textures',{})),separators=(',',':'))+'\n')
            reused+=1
    after_models=list(models.rglob('*.json'))
    print('Runtime assets: removed',removed,'High models;',reused,'geometry models share',len(templates),'templates;',len(after_models),'models;',before_bytes,'->',sum(p.stat().st_size for p in after_models),'JSON bytes')

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--stage',type=Path,required=True);stage(parser.parse_args().stage)
