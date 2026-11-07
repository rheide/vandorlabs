#!/usr/bin/env python3
"""Inactive Storage matching sets, using the released standard-image policy before atlas padding."""
import json, shutil, sys, hashlib
from pathlib import Path
from PIL import Image
from generate_cube_resources import ROOT, source_texture
sys.path.insert(0,str(ROOT/'tools'))
from compact_runtime_assets import standardize
OUT=Path(__file__).resolve().parent/'build/generated/storage-resources'
ASSETS=OUT/'assets/vandorlabs'

def main():
    if OUT.exists():shutil.rmtree(OUT)
    extra=json.loads((ROOT/'generated-resources/assets/vandorlabs/data/unified_textures.json').read_text())
    base_count=(ROOT/'src/portable/java/com/vandorlabs/tiles/HousingFinishCatalog.java').read_text().count('new Finish(')
    sets=[(base_count+i,e) for i,e in enumerate(extra) if e.get('category')=='Storage']
    if len(sets)!=14:raise ValueError('Expected fourteen Source Storage sets')
    textures={}
    for _,entry in sets:
        for key in ['source','side','top']:
            name=entry[key]
            if not name.startswith('storage/') or '..' in name:raise ValueError('Unexpected Storage texture '+name)
            source=source_texture(name)
            if source.with_suffix('.png.mcmeta').exists():raise ValueError('Storage animation requires a proven descriptor')
            target=ASSETS/('textures/blocks/'+name+'.png');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
            textures[name]=target
    standardize(ASSETS/'textures',extra)
    descriptors=[]
    for name,target in sorted(textures.items()):
        with Image.open(target) as source:content=source.convert('RGBA')
        w,h=content.size
        if w!=h or max(w,h)>256:raise ValueError('Unexpected Storage panel dimensions '+name+str(content.size))
        canvas=1
        while canvas<max(w,h):canvas*=2
        image=Image.new('RGBA',(canvas,canvas));image.paste(content,(0,0))
        if w<canvas:image.paste(content.crop((w-1,0,w,h)).resize((canvas-w,h)),(w,0))
        if h<canvas:image.paste(image.crop((0,h-1,canvas,h)).resize((canvas,canvas-h)),(0,h))
        image.save(target,optimize=True)
        descriptors.append(dict(id='vandorlabs:blocks/'+name,width=w,height=h,canvas=canvas,u=w/canvas,v=h/canvas,rgba=hashlib.sha256(content.tobytes()).hexdigest()))
    result=dict(schema=1,textures=descriptors,sets=[dict(choice=choice,front='vandorlabs:blocks/'+e['source'],side='vandorlabs:blocks/'+e['side'],top='vandorlabs:blocks/'+e['top']) for choice,e in sets])
    target=ASSETS/'data/storage_artwork.json';target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(result,indent=2)+'\n')
    print('Native Storage artwork:',len(sets),'sets,',len(descriptors),'Source-named sprites; inactive until a Storage host exists')
if __name__=='__main__':main()
