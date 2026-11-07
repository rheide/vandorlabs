#!/usr/bin/env python3
"""Mirror the released canopy polygon table and its seven authored sheets, without atlas activation."""
import hashlib,json,shutil
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent/'build/generated/canopy-resources/assets/vandorlabs'

def main():
    if OUT.parent.parent.exists():shutil.rmtree(OUT.parent.parent)
    source=ROOT/'src/main/resources/assets/vandorlabs/data/canopy_meshes.json'
    models=json.loads(source.read_text())
    names=sorted({face['material'] for model in models for face in model['faces']})
    if len(models)!=39 or len(names)!=7:raise ValueError('Released canopy model/artwork coverage changed')
    (OUT/'data').mkdir(parents=True)
    shutil.copyfile(source,OUT/'data/canopy_meshes.json')
    kinds=('angled_front_1x1','angled_front_1x2','canopy','long_canopy','regular_canopy_glass','front_canopy_glass','large_front_canopy_glass','space_visor_2x1','space_visor_3x1','space_visor_5x1','space_visor_5x1_double_height')
    for kind in kinds:
        item=OUT/'models/item'/f'{kind}.json';item.parent.mkdir(parents=True,exist_ok=True)
        item.write_text(json.dumps({'parent':'minecraft:block/block','textures':{'particle':'vandorlabs:canopy/canopy_canopy_steel'}},separators=(',',':'))+'\n')
    textures=[]
    for name in names:
        original=ROOT/'texture-packs/additional/assets/vandorlabs/textures/blocks/canopy'/f'{name}.png'
        with Image.open(original) as image:width,height=image.size
        if width!=height or width>256:raise ValueError('Unexpected authored canopy sheet dimensions '+name)
        target=OUT/'textures/canopy'/f'{name}.png';target.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(original,target)
        textures.append({'id':'vandorlabs:canopy/'+name,'original':'vandorlabs:blocks/canopy/'+name,
                         'width':width,'height':height,'sha256':hashlib.sha256(original.read_bytes()).hexdigest()})
    metadata={'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'models':len(models),'textures':textures}
    (OUT/'data/canopy_artwork.json').write_text(json.dumps(metadata,separators=(',',':'))+'\n')
    print('Native inactive canopy resources: 39 authored models, 7 unchanged sheets; no atlas/registry activation')

if __name__=='__main__':main()
